package uk.ac.sanger.sccp.stan.service.workchange;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.Operation;
import uk.ac.sanger.sccp.stan.model.Work;
import uk.ac.sanger.sccp.stan.repo.OperationRepo;
import uk.ac.sanger.sccp.stan.repo.WorkRepo;
import uk.ac.sanger.sccp.stan.request.OpWorkRequest;

import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/** Tests {@link WorkChangeValidationServiceImp} */
class TestWorkChangeValidationService {
    @Mock
    WorkRepo mockWorkRepo;
    @Mock
    OperationRepo mockOpRepo;
    @InjectMocks
    WorkChangeValidationServiceImp service;

    private AutoCloseable mocking;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
        service = spy(service);
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
    }

    @Test
    void testValidate_null() {
        assertValidationException(() -> service.validate(null),
                List.of("No request supplied."));
        verify(service, never()).loadWork(any(), any());
    }

    @Test
    void testValidate_noOpIds() {
        OpWorkRequest request = new OpWorkRequest("SGP1", null);
        doReturn(null).when(service).loadWork(any(), any());
        assertValidationException(() -> service.validate(request),
                List.of("No operations specified."));
        verify(service, never()).loadOps(any(), any(), any());
    }

    @Test
    void testValidate_problems() {
        OpWorkRequest request = new OpWorkRequest("SGP1", List.of(1,2));
        mayAddProblem("Bad work number.", null).when(service).loadWork(any(), any());
        mayAddProblem("Bad op ids.", List.of()).when(service).loadOps(any(), any(), any());
        assertValidationException(() -> service.validate(request),
                List.of("Bad work number.", "Bad op ids."));
        verify(service).loadWork(any(), eq("SGP1"));
        verify(service).loadOps(any(), any(), same(request.getOpIds()));
    }

    private static Operation makeOp(int id) {
        Operation op = new Operation();
        op.setId(id);
        return op;
    }

    @Test
    void testValidate_good() {
        OpWorkRequest request = new OpWorkRequest("SGP1", List.of(1,2));
        Work work = EntityFactory.makeWork("SGP1");
        List<Operation> ops = IntStream.of(1,2)
                .mapToObj(TestWorkChangeValidationService::makeOp).toList();
        doReturn(work).when(service).loadWork(any(), any());
        doReturn(ops).when(service).loadOps(any(), any(), any());
        assertEquals(new WorkChangeData(work, ops), service.validate(request));
        verify(service).loadWork(any(), eq("SGP1"));
        verify(service).loadOps(any(), same(work), same(request.getOpIds()));
    }

    @ParameterizedTest
    @ValueSource(strings={"[null]", "", "SGP1", "SGPX"})
    void testLoadWork(String workNumber) {
        if (workNumber.equalsIgnoreCase("[null]")) {
            workNumber = null;
        }
        Work work = null;
        if (workNumber != null && workNumber.equalsIgnoreCase("SGP1")) {
            work = EntityFactory.makeWork(workNumber);
        }
        String expectedProblem;
        int vtimes = 1;
        if (nullOrEmpty(workNumber)) {
            expectedProblem = "No work number specified.";
            vtimes = 0;
        } else if (work==null) {
            doReturn(Optional.empty()).when(mockWorkRepo).findByWorkNumber(workNumber);
            expectedProblem = String.format("No work found with work number: \"%s\"", workNumber);
        } else {
            doReturn(Optional.of(work)).when(mockWorkRepo).findByWorkNumber(workNumber);
            expectedProblem = null;
        }
        List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        assertSame(work, service.loadWork(problems, workNumber));
        assertProblem(problems, expectedProblem);
        if (vtimes==0) {
            verifyNoInteractions(mockWorkRepo);
        } else {
            verify(mockWorkRepo).findByWorkNumber(workNumber);
        }
    }

    @Test
    void testLoadOps_duplicates() {
        final String dupeError = "Repeated operation ID: 1";
        List<Operation> ops = List.of(makeOp(1), makeOp(2));
        when(mockOpRepo.findAllById(any())).thenReturn(ops);
        Work work = EntityFactory.makeWork("SGP1");
        work.setOperationIds(Set.of(3,4,5));
        List<String> problems = new ArrayList<>(1);
        assertThat(service.loadOps(problems, work, List.of(1,1,2,3)))
                .containsExactly(ops.get(0), ops.get(1));
        assertProblem(problems, dupeError);
        verify(service).dedupe(any(), eq(List.of(1,1,2,3)), eq("operation ID"));
        verify(mockOpRepo).findAllById(List.of(1,2));
    }

    @Test
    void testLoadOps_unknown() {
        List<Integer> opIds = List.of(1,2,3,4);
        List<Operation> ops = List.of(makeOp(1), makeOp(2));
        when(mockOpRepo.findAllById(any())).thenReturn(ops.reversed());
        List<String> problems = new ArrayList<>(1);
        assertThat(service.loadOps(problems, null, opIds)).containsExactlyElementsOf(ops);
        assertProblem(problems, "Unknown operation IDs: [3, 4]");
        verify(service).dedupe(any(), eq(opIds), eq("operation ID"));
        verify(mockOpRepo).findAllById(opIds);
    }

    @Test
    void testLoadOps_allLinked() {
        List<Integer> opIds = List.of(1,2);
        Work work = EntityFactory.makeWork("SGP1");
        work.setOperationIds(Set.of(1,2,3));
        List<String> problems = new ArrayList<>(1);
        assertThat(service.loadOps(problems, work, opIds)).isEmpty();
        assertProblem(problems, "Specified operations are already linked to work SGP1.");
        verify(service).dedupe(any(), eq(opIds), eq("operation ID"));
        verifyNoInteractions(mockOpRepo);
    }

    @Test
    void testLoadOps_valid() {
        List<Integer> opIds = List.of(1,2,3);
        Work work = EntityFactory.makeWork("SGP1");
        work.setOperationIds(Set.of(3,4));
        List<Operation> ops = List.of(makeOp(1), makeOp(2));
        when(mockOpRepo.findAllById(any())).thenReturn(ops.reversed());
        List<String> problems = new ArrayList<>(0);
        assertThat(service.loadOps(problems, work, opIds)).containsExactlyElementsOf(ops);
        assertThat(problems).isEmpty();
        verify(service).dedupe(any(), eq(opIds), eq("operation ID"));
        verify(mockOpRepo).findAllById(List.of(1,2));
    }

    @Test
    void testDedupe() {
        List<String> problems = new ArrayList<>();
        assertThat(service.dedupe(problems, List.of("Alpha", "Beta", "Gamma", "Delta"), "string"))
                .containsExactly("Alpha", "Beta", "Gamma", "Delta");
        assertThat(problems).isEmpty();
        problems = new ArrayList<>(2);
        assertThat(service.dedupe(problems, List.of("Alpha", "Beta", "Gamma", "Beta", "Delta", "Gamma"), "string"))
                .containsExactly("Alpha", "Beta", "Gamma", "Delta");
        assertThat(problems).containsExactly("Repeated string: Beta", "Repeated string: Gamma");
        problems = new ArrayList<>(1);
        assertThat(service.dedupe(problems, Arrays.asList("Alpha", "Beta", null, "Gamma"), "string"))
                .containsExactly("Alpha", "Beta", "Gamma");
        assertThat(problems).containsExactly("Missing string.");
    }
}