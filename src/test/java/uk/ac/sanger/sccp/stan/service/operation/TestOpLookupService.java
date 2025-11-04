package uk.ac.sanger.sccp.stan.service.operation;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Test {@link OpLookupServiceImp} */
class TestOpLookupService {
    @Mock
    OperationTypeRepo mockOpTypeRepo;
    @Mock
    LabwareRepo mockLwRepo;
    @Mock
    WorkRepo mockWorkRepo;
    @Mock
    OperationRepo mockOpRepo;
    @Mock
    LabwareNoteRepo mockLwNoteRepo;

    @InjectMocks
    OpLookupServiceImp service;

    private AutoCloseable mocking;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
    }

    private OperationType mockOpType() {
        OperationType opType = EntityFactory.makeOperationType("Fry", null);
        when(mockOpTypeRepo.findByName(opType.getName())).thenReturn(Optional.of(opType));
        return opType;
    }

    private Labware mockLw() {
        Labware lw = EntityFactory.getTube();
        when(mockLwRepo.findByBarcode(lw.getBarcode())).thenReturn(Optional.of(lw));
        return lw;
    }

    private Work mockWork() {
        Work work = EntityFactory.makeWork("SGP1");
        when(mockWorkRepo.findByWorkNumber(work.getWorkNumber())).thenReturn(Optional.of(work));
        return work;
    }

    private List<Operation> mockOps(int num) {
        final LocalDateTime now = LocalDateTime.now();
        List<Operation> ops = IntStream.range(100, 100+num).mapToObj(i -> {
            Operation op = new Operation();
            op.setId(i);
            op.setPerformed(now);
            return op;
        }).toList();
        when(mockOpRepo.findAllByOperationTypeAndDestinationLabwareIdIn(any(), any())).thenReturn(ops);
        return ops;
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "Bananas"})
    void testFindOpsNoOpType(String opName) {
        if ("null".equals(opName)) {
            opName = null;
        }
        assertThat(service.findOps(opName, "bc", null, null)).isEmpty();
        verifyNoInteractions(mockLwRepo, mockWorkRepo, mockOpRepo, mockLwNoteRepo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "STAN-404"})
    void testFindOpsNoLabware(String barcode) {
        if ("null".equals(barcode)) {
            barcode = null;
        }
        OperationType opType = mockOpType();
        assertThat(service.findOps(opType.getName(), barcode, null, null)).isEmpty();
        verifyNoInteractions(mockWorkRepo, mockOpRepo, mockLwNoteRepo);
    }

    @Test
    void testFindOpsNoSuchWork() {
        Labware lw = mockLw();
        OperationType opType = mockOpType();
        assertThat(service.findOps(opType.getName(), lw.getBarcode(), null, "SGP404")).isEmpty();
        verifyNoInteractions(mockOpRepo, mockLwNoteRepo);
    }

    @ParameterizedTest
    @CsvSource({"true,false", "false,true", "false,false", "true,true"})
    void testFindOpsNoSuchOps(boolean hasRun, boolean hasWork) {
        Labware lw = mockLw();
        OperationType opType = mockOpType();
        String run = (hasRun ? "RUN1" : null);
        Work work = hasWork ? mockWork() : null;
        String workNumber = work!=null ? work.getWorkNumber() : null;
        when(mockOpRepo.findAllByOperationTypeAndDestinationLabwareIdIn(any(), any())).thenReturn(List.of());
        assertThat(service.findOps(opType.getName(), lw.getBarcode(), run, workNumber)).isEmpty();
        verifyNoInteractions(mockLwNoteRepo);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testFindOpsWrongWork(boolean hasRun) {
        Labware lw = mockLw();
        OperationType opType = mockOpType();
        Work work = mockWork();
        String run = (hasRun ? "RUN1" : null);
        mockOps(2);
        work.setOperationIds(Set.of());
        assertThat(service.findOps(opType.getName(), lw.getBarcode(), run, work.getWorkNumber())).isEmpty();
        verify(mockOpRepo).findAllByOperationTypeAndDestinationLabwareIdIn(opType, List.of(lw.getId()));
        verifyNoInteractions(mockLwNoteRepo);
    }

    @Test
    void testFindOps_simple() {
        Labware lw = mockLw();
        OperationType opType = mockOpType();
        List<Operation> ops = mockOps(2);
        assertThat(service.findOps(opType.getName(), lw.getBarcode(), null, null)).containsExactlyElementsOf(ops);
        verify(mockOpTypeRepo).findByName(opType.getName());
        verify(mockLwRepo).findByBarcode(lw.getBarcode());
        verify(mockOpRepo).findAllByOperationTypeAndDestinationLabwareIdIn(opType, List.of(lw.getId()));
        verifyNoInteractions(mockWorkRepo, mockLwNoteRepo);
    }

    @Test
    void testFindOps_full() {
        Labware lw = mockLw();
        OperationType opType = mockOpType();
        Work work = mockWork();
        String run = "RUN1";
        List<Operation> ops = mockOps(5);
        work.setOperationIds(Set.of(ops.get(0).getId(), ops.get(1).getId(), ops.get(2).getId(), ops.get(3).getId()));
        List<LabwareNote> lwNotes = IntStream.of(1,2,3,4)
                .mapToObj(i -> new LabwareNote(200+i, lw.getId(), ops.get(i).getId(), "run", (i < 3 ? run : "RUN2")))
                .toList();
        when(mockLwNoteRepo.findAllByLabwareIdInAndName(List.of(lw.getId()), "run")).thenReturn(lwNotes);

        assertThat(service.findOps(opType.getName(), lw.getBarcode(), run, work.getWorkNumber()))
                .containsExactlyInAnyOrder(ops.get(1), ops.get(2));

        verify(mockOpTypeRepo).findByName(opType.getName());
        verify(mockLwRepo).findByBarcode(lw.getBarcode());
        verify(mockWorkRepo).findByWorkNumber(work.getWorkNumber());
        verify(mockOpRepo).findAllByOperationTypeAndDestinationLabwareIdIn(opType, List.of(lw.getId()));
        verify(mockLwNoteRepo).findAllByLabwareIdInAndName(List.of(lw.getId()), "run");
    }
}