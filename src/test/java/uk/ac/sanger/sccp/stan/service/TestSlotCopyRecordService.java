package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.slotcopyrecord.SlotCopyRecord;
import uk.ac.sanger.sccp.stan.model.slotcopyrecord.SlotCopyRecordNote;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest.SlotCopyContent;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest.SlotCopySource;
import uk.ac.sanger.sccp.stan.request.SlotCopySave;

import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.addProblem;
import static uk.ac.sanger.sccp.stan.Matchers.assertValidationException;
import static uk.ac.sanger.sccp.stan.service.SlotCopyRecordServiceImp.*;

/**
 * Test {@link SlotCopyRecordServiceImp}
 */
class TestSlotCopyRecordService {
    @Mock
    private SlotCopyRecordRepo mockRecordRepo;
    @Mock
    private OperationTypeRepo mockOpTypeRepo;
    @Mock
    private WorkRepo mockWorkRepo;
    @Mock
    private EntityManager mockEntityManager;

    @InjectMocks
    private SlotCopyRecordServiceImp service;

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

    @ParameterizedTest
    @ValueSource(booleans={true, false})
    void testSave(boolean exists) {
        Work work = EntityFactory.makeWork("SGP1");
        when(mockWorkRepo.getByWorkNumber(work.getWorkNumber())).thenReturn(work);
        OperationType opType = EntityFactory.makeOperationType("opname", null);
        when(mockOpTypeRepo.getByName(opType.getName())).thenReturn(opType);
        SlotCopySave save = new SlotCopySave();
        save.setOperationType(opType.getName());
        save.setWorkNumber(work.getWorkNumber());
        final String LP = "LP1";
        save.setLpNumber(LP);

        doNothing().when(service).checkRequiredFields(any(), any());

        SlotCopyRecord existing;
        if (exists) {
            existing = new SlotCopyRecord();
            existing.setLpNumber(LP);
            existing.setOperationType(opType);
            existing.setWork(work);
            existing.setId(700);
            when(mockRecordRepo.findByOperationTypeAndWorkAndLpNumber(any(), any(), any())).thenReturn(Optional.of(existing));
        } else {
            existing = null;
            when(mockRecordRepo.findByOperationTypeAndWorkAndLpNumber(any(), any(), any())).thenReturn(Optional.empty());
        }
        List<SlotCopyRecordNote> notes = List.of(new SlotCopyRecordNote("Alpha", "Beta"));
        doReturn(notes).when(service).createNotes(any());
        when(mockRecordRepo.save(any())).then(invocation -> {
            SlotCopyRecord record = invocation.getArgument(0);
            record.setId(800);
            return record;
        });

        SlotCopyRecord record = service.save(save);

        verify(service).checkRequiredFields(any(), same(save));
        verify(mockWorkRepo).getByWorkNumber(work.getWorkNumber());
        verify(mockOpTypeRepo).getByName(opType.getName());
        verify(mockRecordRepo).findByOperationTypeAndWorkAndLpNumber(opType, work, LP);

        if (exists) {
            verify(mockRecordRepo).delete(existing);
            verify(mockEntityManager).flush();
        } else {
            verify(mockRecordRepo, never()).delete(any());
        }

        assertNotNull(record.getId());
        assertEquals(opType, record.getOperationType());
        assertEquals(work, record.getWork());
        assertEquals(LP, record.getLpNumber());
        assertThat(record.getNotes()).containsExactlyInAnyOrderElementsOf(notes);
        verify(mockRecordRepo).save(record);
    }

    @Test
    void testSave_invalid() {
        SlotCopySave save = new SlotCopySave();
        doAnswer(addProblem("Bad stuff")).when(service).checkRequiredFields(any(), any());
        assertValidationException(() -> service.save(save), List.of("Bad stuff"));
        verifyNoMoreInteractions(mockRecordRepo);
    }

    @ParameterizedTest
    @ValueSource(booleans={true, false})
    void testLoad(boolean found) {
        Work work = EntityFactory.makeWork("SGP1");
        when(mockWorkRepo.getByWorkNumber(work.getWorkNumber())).thenReturn(work);
        OperationType opType = EntityFactory.makeOperationType("opname", null);
        when(mockOpTypeRepo.getByName(opType.getName())).thenReturn(opType);
        String LP = "LP1";
        SlotCopyRecord record;
        SlotCopySave save;
        if (found) {
            record = new SlotCopyRecord();
            record.setId(500);
            save = new SlotCopySave();
            save.setOperationType(opType.getName());
            doReturn(save).when(service).reassembleSave(any());
        } else {
            record = null;
            save = null;
        }
        when(mockRecordRepo.findByOperationTypeAndWorkAndLpNumber(any(), any(), any())).thenReturn(Optional.ofNullable(record));

        if (found) {
            assertSame(save, service.load(opType.getName(), work.getWorkNumber(), LP));
            verify(service).reassembleSave(record);
        } else {
            assertThrows(EntityNotFoundException.class, () -> service.load(opType.getName(), work.getWorkNumber(), LP));
            verify(service, never()).reassembleSave(any());
        }
        verify(mockRecordRepo).findByOperationTypeAndWorkAndLpNumber(opType, work, LP);
    }

    @Test
    void testReassembleSave() {
        SlotCopyRecord record = new SlotCopyRecord();
        Work work = EntityFactory.makeWork("SGP1");
        OperationType opType = EntityFactory.makeOperationType("opname", null);
        record.setWork(work);
        record.setOperationType(opType);
        record.setLpNumber("LP1");
        record.setNotes(List.of(
                new SlotCopyRecordNote(NOTE_BARCODE, "STAN-A"),
                new SlotCopyRecordNote(NOTE_LWTYPE, "lwtype"),
                new SlotCopyRecordNote(NOTE_PREBARCODE, "pb"),
                new SlotCopyRecordNote(NOTE_BIOSTATE, "bs"),
                new SlotCopyRecordNote(NOTE_COSTING, "SGP"),
                new SlotCopyRecordNote(NOTE_LOT, "lot1"),
                new SlotCopyRecordNote(NOTE_PROBELOT, "probe1"),
                new SlotCopyRecordNote(NOTE_REAGENT_A_LOT, "rla"),
                new SlotCopyRecordNote(NOTE_REAGENT_B_LOT, "rlb"),
                new SlotCopyRecordNote(NOTE_CASSETTE_LOT, "caslot"),
                new SlotCopyRecordNote(NOTE_EXECUTION, "manual"),
                new SlotCopyRecordNote(NOTE_SRC_BARCODE, 0, "STAN-0"),
                new SlotCopyRecordNote(NOTE_SRC_STATE, 0, "discarded"),
                new SlotCopyRecordNote(NOTE_SRC_BARCODE, 1, "STAN-1"),
                new SlotCopyRecordNote(NOTE_SRC_STATE, 1, "active"),
                new SlotCopyRecordNote(NOTE_CON_SRCBC, 0,"STAN-0"),
                new SlotCopyRecordNote(NOTE_CON_SRCADDRESS, 0, "A1"),
                new SlotCopyRecordNote(NOTE_CON_DESTADDRESS, 0, "A2"),
                new SlotCopyRecordNote(NOTE_CON_SRCBC, 1, "STAN-1"),
                new SlotCopyRecordNote(NOTE_CON_SRCADDRESS, 1, "B1"),
                new SlotCopyRecordNote(NOTE_CON_DESTADDRESS, 1, "B2")
        ));
        SlotCopySave save = service.reassembleSave(record);

        assertEquals("SGP1", save.getWorkNumber());
        assertEquals("opname", save.getOperationType());
        assertEquals("LP1", save.getLpNumber());
        assertEquals("STAN-A", save.getBarcode());
        assertEquals("lwtype", save.getLabwareType());
        assertEquals("pb", save.getPreBarcode());
        assertEquals("bs", save.getBioState());
        assertEquals(SlideCosting.SGP, save.getCosting());
        assertEquals("lot1", save.getLotNumber());
        assertEquals("probe1", save.getProbeLotNumber());
        assertEquals("rla", save.getReagentALot());
        assertEquals("rlb", save.getReagentBLot());
        assertEquals("caslot", save.getCassetteLot());
        assertEquals(ExecutionType.manual, save.getExecutionType());
        assertThat(save.getSources()).containsExactlyInAnyOrder(new SlotCopySource("STAN-0", Labware.State.discarded),
                new SlotCopySource("STAN-1", Labware.State.active));
        assertThat(save.getContents()).containsExactlyInAnyOrder(new SlotCopyContent("STAN-0", new Address(1,1), new Address(1,2)),
                new SlotCopyContent("STAN-1", new Address(2,1), new Address(2,2)));
    }

    @ParameterizedTest
    @CsvSource({",","'',", "'1',1"})
    void testNullableValueOf(String string, Integer expected) {
        assertEquals(expected, nullableValueOf(string, Integer::valueOf));
    }

    @ParameterizedTest
    @CsvSource({"'  Alpha  ',Alpha", "Beta,Beta", ",", "'   ',"})
    void testTrimAndCheck(String string, String expected) {
        List<String> problems = new ArrayList<>(expected==null ? 1 : 0);
        assertEquals(expected, service.trimAndCheck(problems, "thing", string));
        if (expected==null) {
            assertThat(problems).containsExactly("Missing thing.");
        } else {
            assertThat(problems).isEmpty();
        }
    }

    @ParameterizedTest
    @CsvSource({"true,true,true", "false,true,true", "true,false,true", "true,true,false", "false,false,false"})
    void testCheckRequiredFields(boolean workPresent, boolean opTypePresent, boolean lpPresent) {
        SlotCopySave request = new SlotCopySave();
        request.setWorkNumber(workPresent ? "  SGP1 " : null);
        request.setOperationType(opTypePresent ? "  opname " : "");
        request.setLpNumber(lpPresent ? "  LP1 " : "   ");
        List<String> problems = new ArrayList<>();
        service.checkRequiredFields(problems, request);
        assertEquals(workPresent ? "SGP1":null, request.getWorkNumber());
        assertEquals(opTypePresent ? "opname" : null, request.getOperationType());
        assertEquals(lpPresent ? "LP1" : null, request.getLpNumber());

        if (workPresent && opTypePresent && lpPresent) {
            assertThat(problems).isEmpty();
        }
        if (!workPresent) {
            assertThat(problems).contains("Missing work number.");
        }
        if (!opTypePresent) {
            assertThat(problems).contains("Missing operation type.");
        }
        if (!lpPresent) {
            assertThat(problems).contains("Missing LP number.");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false,true})
    void testMayAddNote(boolean valuePresent) {
        List<SlotCopyRecordNote> notes = new ArrayList<>();
        service.mayAddNote(notes, "name", valuePresent ? "value" : null);
        if (valuePresent) {
            assertThat(notes).containsExactly(new SlotCopyRecordNote("name", "value"));
        } else {
            assertThat(notes).isEmpty();
        }
    }

    @Test
    void testCreateNotes() {
        SlotCopySave request = new SlotCopySave();
        request.setOperationType("opname");
        request.setWorkNumber("SGP1");
        request.setLpNumber("LP1");
        request.setCosting(SlideCosting.Faculty);
        request.setExecutionType(ExecutionType.automated);
        request.setBarcode("STAN-A");
        request.setBioState("bs");
        request.setLabwareType("lwtype");
        request.setPreBarcode("pb");
        request.setLotNumber("lot1");
        request.setProbeLotNumber("probe1");
        request.setReagentALot("rla");
        request.setReagentBLot("rlb");
        request.setCassetteLot("caslot");
        request.setSources(List.of(new SlotCopySource("STAN-0", Labware.State.discarded),
                new SlotCopySource("STAN-1", Labware.State.active)));
        request.setContents(List.of(
                new SlotCopyContent("STAN-0", new Address(1, 1), new Address(1, 2)),
                new SlotCopyContent("STAN-1", new Address(2, 1), new Address(2, 2))
        ));
        List<SlotCopyRecordNote> notes = service.createNotes(request);
        assertThat(notes).containsExactlyInAnyOrder(
                new SlotCopyRecordNote(NOTE_COSTING, "Faculty"),
                new SlotCopyRecordNote(NOTE_EXECUTION, "automated"),
                new SlotCopyRecordNote(NOTE_BARCODE, "STAN-A"),
                new SlotCopyRecordNote(NOTE_BIOSTATE, "bs"),
                new SlotCopyRecordNote(NOTE_LWTYPE, "lwtype"),
                new SlotCopyRecordNote(NOTE_PREBARCODE, "pb"),
                new SlotCopyRecordNote(NOTE_LOT, "lot1"),
                new SlotCopyRecordNote(NOTE_PROBELOT, "probe1"),
                new SlotCopyRecordNote(NOTE_REAGENT_A_LOT, "rla"),
                new SlotCopyRecordNote(NOTE_REAGENT_B_LOT, "rlb"),
                new SlotCopyRecordNote(NOTE_CASSETTE_LOT, "caslot"),
                new SlotCopyRecordNote(NOTE_SRC_BARCODE, 0, "STAN-0"),
                new SlotCopyRecordNote(NOTE_SRC_STATE, 0, "discarded"),
                new SlotCopyRecordNote(NOTE_SRC_BARCODE, 1, "STAN-1"),
                new SlotCopyRecordNote(NOTE_SRC_STATE, 1, "active"),
                new SlotCopyRecordNote(NOTE_CON_SRCBC, 0, "STAN-0"),
                new SlotCopyRecordNote(NOTE_CON_SRCADDRESS, 0, "A1"),
                new SlotCopyRecordNote(NOTE_CON_DESTADDRESS, 0, "A2"),
                new SlotCopyRecordNote(NOTE_CON_SRCBC, 1, "STAN-1"),
                new SlotCopyRecordNote(NOTE_CON_SRCADDRESS, 1, "B1"),
                new SlotCopyRecordNote(NOTE_CON_DESTADDRESS, 1, "B2")
        );
    }

    @Test
    void testLoadNoteMap() {
        List<SlotCopyRecordNote> notes = List.of(
                new SlotCopyRecordNote("Alpha", "Alabama"),
                new SlotCopyRecordNote("Beta", "Banana"),
                new SlotCopyRecordNote("Gamma", 2, "G2"),
                new SlotCopyRecordNote("Gamma", 0, "G0")
        );
        Map<String, List<String>> map = service.loadNoteMap(notes);
        assertThat(map).hasSize(3);
        assertThat(map.get("Alpha")).containsExactly("Alabama");
        assertThat(map.get("Beta")).containsExactly("Banana");
        assertThat(map.get("Gamma")).containsExactly("G0", null, "G2");
    }

    @Test
    void testSingleNoteValue() {
        Map<String, List<String>> map = Map.of("Alpha", List.of("Alabama"), "Beta", List.of());
        assertEquals("Alabama", service.singleNoteValue(map, "Alpha"));
        assertNull(service.singleNoteValue(map, "Beta"));
        assertNull(service.singleNoteValue(map, "Gamma"));
    }
}