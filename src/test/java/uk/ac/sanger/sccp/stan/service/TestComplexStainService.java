package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.stain.*;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.service.ComplexStainServiceImp.STAIN_IHC;
import static uk.ac.sanger.sccp.stan.service.ComplexStainServiceImp.STAIN_RNASCOPE;

/**
 * Tests {@link ComplexStainServiceImp}
 */
public class TestComplexStainService {
    private WorkService mockWorkService;
    private OperationService mockOpService;
    private LabwareValidatorFactory mockLwValFactory;
    private OperationTypeRepo mockOpTypeRepo;
    private StainTypeRepo mockStainTypeRepo;
    private LabwareRepo mockLwRepo;
    private LabwareNoteRepo mockLwNoteRepo;

    private ComplexStainServiceImp service;

    @BeforeEach
    void setup() {
        mockWorkService = mock(WorkService.class);
        mockOpService = mock(OperationService.class);
        mockLwValFactory = mock(LabwareValidatorFactory.class);
        mockOpTypeRepo = mock(OperationTypeRepo.class);
        mockStainTypeRepo = mock(StainTypeRepo.class);
        mockLwRepo = mock(LabwareRepo.class);
        mockLwNoteRepo = mock(LabwareNoteRepo.class);

        service = spy(new ComplexStainServiceImp(mockWorkService, mockOpService, mockLwValFactory, mockOpTypeRepo,
                mockStainTypeRepo, mockLwRepo, mockLwNoteRepo));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testPerform(boolean valid) {
        User user = EntityFactory.getUser();
        Labware lw = EntityFactory.getTube();
        OperationType opType = EntityFactory.makeOperationType("Stain", null, OperationTypeFlag.IN_PLACE, OperationTypeFlag.STAIN);
        StainType stainType = new StainType(3, "RNAscope");
        Work work = new Work(50, "SGP50", null, null, null, Work.Status.active);
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw);
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, work);

        doReturn(opType).when(service).loadStainOpType(any());
        doReturn(lwMap).when(service).loadLabware(any(), any());
        doReturn(stainType).when(service).loadStainType(any(), any());
        doNothing().when(service).validatePanel(any(), any());
        doNothing().when(service).validatePlex(any(), anyInt());
        doReturn(workMap).when(service).loadWorks(any(), any());
        doNothing().when(service).validateBondRuns(any(), any());
        if (valid) {
            doNothing().when(service).validateBondBarcodes(any(), any());
        } else {
            doAnswer(invocation -> {
                Collection<String> problems = invocation.getArgument(0);
                problems.add("Everything is bad.");
                return null;
            }).when(service).validateBondBarcodes(any(), any());
        }

        ComplexStainRequest request = new ComplexStainRequest(
                stainType.getName(), 4, StainPanel.positive, List.of(
                new ComplexStainLabware(lw.getBarcode(), "0123 ABC", 5, work.getWorkNumber())
        ));

        if (valid) {
            OperationResult opres = new OperationResult(List.of(), List.of(lw));
            doReturn(opres).when(service).record(any(), any(), any(), any(), any(), any());

            assertSame(opres, service.perform(user, request));
        } else {
            Matchers.assertValidationException(() -> service.perform(user, request),
                    "The request could not be validated.", "Everything is bad.");
        }
        //noinspection unchecked
        ArgumentCaptor<Collection<String>> problemsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(service).loadStainOpType(problemsCaptor.capture());
        Collection<String> problems = problemsCaptor.getValue();
        verify(service).loadLabware(same(problems), same(request.getLabware()));
        verify(service).loadStainType(same(problems), eq(request.getStainType()));
        verify(service).validatePanel(same(problems), eq(request.getPanel()));
        verify(service).validatePlex(same(problems), eq(request.getPlex()));
        verify(service).loadWorks(same(problems), same(request.getLabware()));
        verify(service).validateBondRuns(same(problems), same(request.getLabware()));
        verify(service).validateBondBarcodes(same(problems), same(request.getLabware()));
        if (valid) {
            verify(service).record(user, request, opType, stainType, lwMap, workMap);
        } else {
            verify(service, never()).record(any(), any(), any(), any(), any(), any());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "positive", "negative", "marker", "null"})
    public void testValidatePanel(String panelName) {
        StainPanel panel = (panelName.equals("null") ? null : StainPanel.valueOf(panelName));
        final List<String> problems = new ArrayList<>(panel==null ? 1 : 0);
        service.validatePanel(problems, panel);
        if (panel==null) {
            assertThat(problems).containsExactly("No experiment panel specified.");
        } else {
            assertThat(problems).isEmpty();
        }
    }

    @ParameterizedTest
    @CsvSource({"-1,false", "0,false", "1,true", "2,true", "99,true", "100,true", "101,false", "200,false"})
    public void testValidatePlex(int plex, boolean valid) {
        final List<String> problems = new ArrayList<>(valid ? 0 : 1);
        service.validatePlex(problems, plex);
        if (valid) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactly("The plex number ("+plex+") should be in the range 1-100.");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testLoadWorks(boolean valid) {
        Work work1 = new Work(50, "SGP50", null, null, null, Work.Status.active);
        Work work2 = new Work(51, "SGP51", null, null, null, Work.Status.active);
        List<ComplexStainLabware> csls = List.of(
                new ComplexStainLabware("STAN-01", "0000-000", 1, work1.getWorkNumber()),
                new ComplexStainLabware("STAN-02", "0000-000", 1, work1.getWorkNumber()),
                new ComplexStainLabware("STAN-03", "0000-000", 1, work2.getWorkNumber()),
                new ComplexStainLabware("STAN-04", "0000-000", 1, null)
        );
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, work1, work2);
        if (valid) {
            when(mockWorkService.validateUsableWorks(any(), any())).thenReturn(workMap);
        } else {
            when(mockWorkService.validateUsableWorks(any(), any())).then(invocation -> {
                Collection<String> problems = invocation.getArgument(0);
                problems.add("Bad work.");
                return workMap;
            });
        }
        final List<String> problems = new ArrayList<>(valid ? 0 : 1);
        assertSame(workMap, service.loadWorks(problems, csls));
        if (valid) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactly("Bad work.");
        }
        verify(mockWorkService).validateUsableWorks(problems, Set.of(work1.getWorkNumber(), work2.getWorkNumber()));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testValidateBondRuns(boolean valid) {
        int[] runs = (valid ? new int[] { 1,2,1,9999 } : new int[] { 1,-3,0,-3,10_000 });
        List<ComplexStainLabware> csls = IntStream.range(0, runs.length)
                .mapToObj(i -> new ComplexStainLabware("STAN-"+i, "000-0000", runs[i], null))
                .collect(toList());
        List<String> problems = new ArrayList<>(valid ? 0 : 1);
        service.validateBondRuns(problems, csls);
        if (valid) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactly("Bond runs are expected to be in the range 1-9999: [-3, 0, 10000]");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testValidateBondBarcodes(boolean valid) {
        String[] bondBarcodes = (valid? new String[] { "0000 000", "0000 000", "099A AZZ"}
                : new String[] { null, "1234 ABC", "0000 00%", "0000 00%", "1111 11", "2222 2222" });
        List<ComplexStainLabware> csls = IntStream.range(0, bondBarcodes.length)
                .mapToObj(i -> new ComplexStainLabware("STAN-"+i, bondBarcodes[i], 1, null))
                .collect(toList());
        List<String> problems = new ArrayList<>(valid ? 0 : 1);
        service.validateBondBarcodes(problems, csls);
        if (valid) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactly("Bond barcodes not of the expected format: [null, 0000 00%, 1111 11, 2222 2222]");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testLoadLabware(boolean valid) {
        String[] barcodes = new String[] {"STAN-1", "STAN-2", "STAN-1", null};
        List<ComplexStainLabware> csls = Arrays.stream(barcodes)
                .map(bc -> new ComplexStainLabware(bc, "0000-000", 1, null))
                .collect(toList());
        LabwareValidator val = mock(LabwareValidator.class);
        when(mockLwValFactory.getValidator()).thenReturn(val);
        List<String> errors = valid ? List.of() : List.of("Missing barcode.", "Repeated barcode.");
        when(val.getErrors()).thenReturn(errors);
        Labware lw1 = EntityFactory.getTube();
        Labware lw2 = EntityFactory.makeEmptyLabware(lw1.getLabwareType());
        when(val.getLabware()).thenReturn(List.of(lw1, lw2));

        List<String> problems = new ArrayList<>(errors.size());
        UCMap<Labware> lwMap = service.loadLabware(problems, csls);

        assertThat(lwMap).hasSize(2);
        assertEquals(lw1, lwMap.get(lw1.getBarcode()));
        assertEquals(lw2, lwMap.get(lw2.getBarcode()));
        assertThat(problems).containsExactlyElementsOf(errors);

        verify(val).loadLabware(same(mockLwRepo), eq(Arrays.asList(barcodes)));
        verify(val).setUniqueRequired(true);
        verify(val).validateSources();
        verify(val).getErrors();
        verify(val).getLabware();
    }

    @Test
    public void testLoadLabware_none() {
        final List<String> problems = new ArrayList<>(1);
        assertThat(service.loadLabware(problems, List.of())).isEmpty();
        verifyNoInteractions(mockLwValFactory);
        assertThat(problems).containsExactly("No labware specified.");
    }

    @ParameterizedTest
    @CsvSource({
            "false, false, false, Stain operation type not found.",
            "true, true, false, Stain operation type does not have the stain flag.",
            "true, false, true, Stain operation type cannot be recorded in place.",
            "true, false, false, Stain operation type does not have the stain flag.;Stain operation type cannot be recorded in place.",
            "true, true, true,",
    })
    public void testLoadStainOpType(boolean exists, boolean inPlace, boolean stain, String expectedErrorsJoined) {
        OperationType opType;
        if (!exists) {
            opType = null;
        } else {
            opType = EntityFactory.makeOperationType("Stain", null);
            int flags = 0;
            if (inPlace) {
                flags |= OperationTypeFlag.IN_PLACE.bit();
            }
            if (stain) {
                flags |= OperationTypeFlag.STAIN.bit();
            }
            opType.setFlags(flags);
        }
        String[] expectedErrors = expectedErrorsJoined==null ? new String[0] : expectedErrorsJoined.split(";");
        when(mockOpTypeRepo.findByName("Stain")).thenReturn(Optional.ofNullable(opType));
        List<String> problems = new ArrayList<>(expectedErrors.length);
        assertSame(opType, service.loadStainOpType(problems));
        assertThat(problems).containsExactlyInAnyOrder(expectedErrors);
    }

    @ParameterizedTest
    @ValueSource(strings={"", "null", "Bananas", "RNAscope", "IHC", "H&E"})
    public void testLoadStainType(String name) {
        if (name.equals("null")) {
            name = null;
        }
        if (name==null || name.isEmpty()) {
            List<String> problems = new ArrayList<>(1);
            assertNull(service.loadStainType(problems, name));
            assertThat(problems).containsExactly("No stain type specified.");
            verifyNoInteractions(mockStainTypeRepo);
            return;
        }
        StainType stainType;
        String expectedError;
        switch (name) {
            case STAIN_RNASCOPE: case STAIN_IHC:
                stainType = new StainType(3, name);
                expectedError = null;
                break;
            case "H&E":
                stainType = new StainType(4, name);
                expectedError = "The stain type "+name+" was not expected for this type of request.";
                break;
            default:
                stainType = null;
                expectedError = "Unknown stain type: \""+name+"\"";
                break;
        }
        List<String> problems = new ArrayList<>(expectedError==null ? 0 : 1);
        when(mockStainTypeRepo.findByName(name)).thenReturn(Optional.ofNullable(stainType));
        assertSame(stainType, service.loadStainType(problems, name));
        if (expectedError==null) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactly(expectedError);
        }
    }

    @Test
    public void testRecord() {
        User user = EntityFactory.getUser();
        OperationType opType = EntityFactory.makeOperationType("Stain", null, OperationTypeFlag.IN_PLACE, OperationTypeFlag.STAIN);
        StainType stainType = new StainType(10, "RNAscope");
        Work work1 = new Work(1, "SGP1", null, null, null, Work.Status.active);
        Work work2 = new Work(2, "SGP2", null, null, null, Work.Status.active);
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, work1, work2);
        LabwareType lt = EntityFactory.getTubeType();
        Labware[] lws = IntStream.range(0,4)
                .mapToObj(i -> EntityFactory.makeEmptyLabware(lt))
                .toArray(Labware[]::new);
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lws);
        List<ComplexStainLabware> csls = List.of(
                new ComplexStainLabware(lws[0].getBarcode(), "0000-000", 1, work1.getWorkNumber()),
                new ComplexStainLabware(lws[1].getBarcode(), "0000-001", 2, work1.getWorkNumber()),
                new ComplexStainLabware(lws[2].getBarcode(), "0000-002", 3, work2.getWorkNumber()),
                new ComplexStainLabware(lws[3].getBarcode(), "0000-003", 3, null)
        );
        ComplexStainRequest request = new ComplexStainRequest(stainType.getName(), 10, StainPanel.positive, csls);

        Operation[] ops = IntStream.range(0, lws.length)
                .mapToObj(i -> {
                    Operation op = new Operation(200+i, opType, null, null, user);
                    doReturn(op).when(service).createOp(user, lws[i], opType, stainType);
                    return op;
                })
                .toArray(Operation[]::new);
        doNothing().when(service).recordLabwareNotes(any(), any(), anyInt(), anyInt());
        when(mockWorkService.link(any(Work.class), any())).then(Matchers.returnArgument());

        OperationResult opres = service.record(user, request, opType, stainType, lwMap, workMap);
        assertThat(opres.getLabware()).containsExactly(lws);
        assertThat(opres.getOperations()).containsExactly(ops);

        for (int i = 0; i < lws.length; ++i) {
            Labware lw = lws[i];
            Operation op = ops[i];
            verify(service).createOp(user, lw, opType, stainType);
            verify(service).recordLabwareNotes(request, csls.get(i), lw.getId(), op.getId());
        }

        verify(mockWorkService).link(work1, List.of(ops[0], ops[1]));
        verify(mockWorkService).link(work2, List.of(ops[2]));
        verifyNoMoreInteractions(mockWorkService);
    }

    @Test
    public void testCreateOp() {
        User user = EntityFactory.getUser();
        OperationType opType = EntityFactory.makeOperationType("Stain", null);
        Labware lw = EntityFactory.getTube();
        StainType stainType = new StainType(40, "RNAscope");
        final int opId = 40;
        when(mockOpService.createOperationInPlace(same(opType), same(user), same(lw), isNull(), any())).thenAnswer(invocation -> {
            Operation op = new Operation();
            op.setId(opId);
            Consumer<Operation> opModifier = invocation.getArgument(4);
            opModifier.accept(op);
            return op;
        });

        Operation op = service.createOp(user, lw, opType, stainType);
        assertEquals(opId, op.getId());
        assertSame(stainType, op.getStainType());
    }

    @Test
    public void testRecordLabwareNotes() {
        ComplexStainLabware csl = new ComplexStainLabware("STAN-01", "1234 ABC", 23, null);
        ComplexStainRequest request = new ComplexStainRequest("RNAscope", 17, StainPanel.negative, List.of(csl));
        final int lwId = 200;
        final int opId = 400;
        service.recordLabwareNotes(request, csl, lwId, opId);
        verify(mockLwNoteRepo).saveAll(Matchers.sameElements(List.of(
                new LabwareNote(null, lwId, opId, ComplexStainServiceImp.LW_NOTE_PANEL, "negative"),
                new LabwareNote(null, lwId, opId, ComplexStainServiceImp.LW_NOTE_BOND_BARCODE, csl.getBondBarcode()),
                new LabwareNote(null, lwId, opId, ComplexStainServiceImp.LW_NOTE_PLEX, "17"),
                new LabwareNote(null, lwId, opId, ComplexStainServiceImp.LW_NOTE_BOND_RUN, "23")
        )));
    }
}
