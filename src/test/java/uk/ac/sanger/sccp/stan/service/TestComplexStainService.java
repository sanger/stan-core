package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
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
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
        List<StainType> stainTypes = List.of(new StainType(3, "RNAscope"));
        Work work = new Work(50, "SGP50", null, null, null, null, Work.Status.active);
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw);
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, work);

        doReturn(opType).when(service).loadStainOpType(any());
        doReturn(lwMap).when(service).loadLabware(any(), any());
        doReturn(stainTypes).when(service).loadStainTypes(any(), any());
        doReturn(workMap).when(service).loadWorks(any(), any());
        doNothing().when(service).validateBondRuns(any(), any());
        doNothing().when(service).validatePlexes(any(), any(), any());
        doNothing().when(service).validatePanels(any(), any());
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
                List.of("RNAscope"), List.of(
                new ComplexStainLabware(lw.getBarcode(), "0123 ABC", 5, work.getWorkNumber(),
                        4, null, StainPanel.positive)
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
        verify(service).loadStainTypes(same(problems), same(request.getStainTypes()));
        verify(service).validatePanels(same(problems), same(request.getLabware()));
        verify(service).validatePlexes(same(problems), eq(stainTypes), same(request.getLabware()));
        verify(service).loadWorks(same(problems), same(request.getLabware()));
        verify(service).validateBondRuns(same(problems), same(request.getLabware()));
        verify(service).validateBondBarcodes(same(problems), same(request.getLabware()));
        if (valid) {
            verify(service).record(user, request, opType, stainTypes, lwMap, workMap);
        } else {
            verify(service, never()).record(any(), any(), any(), any(), any(), any());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testLoadWorks(boolean valid) {
        Work work1 = new Work(50, "SGP50", null, null, null, null, Work.Status.active);
        Work work2 = new Work(51, "SGP51", null, null, null, null, Work.Status.active);
        List<ComplexStainLabware> csls = List.of(
                new ComplexStainLabware("STAN-01", "0000-000", 1, work1.getWorkNumber(), null, null, null),
                new ComplexStainLabware("STAN-02", "0000-000", 1, work1.getWorkNumber(), null, null, null),
                new ComplexStainLabware("STAN-03", "0000-000", 1, work2.getWorkNumber(), null, null, null),
                new ComplexStainLabware("STAN-04", "0000-000", 1, work2.getWorkNumber(), null, null, null)
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
    @MethodSource("loadStainTypesArgs")
    public void testLoadStainTypes(UCMap<StainType> stainTypeMap, List<String> stainNames,
                                   List<StainType> expectedStainTypes, List<String> expectedProblems) {
        when(mockStainTypeRepo.findAllByNameIn(any())).then(invocation -> {
            List<String> names = invocation.getArgument(0);
            return names.stream()
                    .map(stainTypeMap::get)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(toList());
        });

        final List<String> problems = new ArrayList<>(expectedProblems.size());
        assertThat(service.loadStainTypes(problems, stainNames)).containsExactlyInAnyOrderElementsOf(expectedStainTypes);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> loadStainTypesArgs() {
        StainType he = new StainType(1, "H&E");
        StainType mt = new StainType(2, "Masson's Trichrome");
        StainType rna = new StainType(3, "RNAscope");
        StainType ihc = new StainType(4, "IHC");

        UCMap<StainType> stainTypeMap = UCMap.from(StainType::getName, he, mt, rna, ihc);

        return Arrays.stream(new Object[][][] {
                {{"RNAscope"}, {rna}, {}},
                {{"IHC"}, {ihc}, {}},
                {{"rnascope", "ihc"}, {rna, ihc}, {}},
                {{}, {}, {"No stain types specified."}},
                {null, {}, {"No stain types specified."}},
                {{"rnascope", "ihc", "RNAscope"}, {rna, ihc}, {"Repeated stain type: [\"RNAscope\"]"}},
                {{"rnascope", "bananas", "custard"}, {rna}, {"Unknown stain type: [\"bananas\", \"custard\"]"}},
                {{"h&e", "IHC", "masson's trichrome"}, {ihc, he, mt},
                        {"The supplied stain type was not expected for this request: [H&E, Masson's Trichrome]"}},
                {{"rnascope", "IHC", "IHC", "H&E", "Bananas"}, {rna, ihc, he},
                        {"Unknown stain type: [\"Bananas\"]",
                        "Repeated stain type: [\"IHC\"]",
                        "The supplied stain type was not expected for this request: [H&E]"}},
        }).map(arr -> {
            List<String> stainNames = arr[0]==null ? null : Arrays.stream(arr[0]).map(x -> (String) x).collect(toList());
            List<StainType> stainTypes = Arrays.stream(arr[1]).map(x -> (StainType) x).collect(toList());
            List<String> problems = Arrays.stream(arr[2]).map(x -> (String) x).collect(toList());
            return Arguments.of(stainTypeMap, stainNames, stainTypes, problems);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testValidateBondRuns(boolean valid) {
        int[] runs = (valid ? new int[] { 1,2,1,9999 } : new int[] { 1,-3,0,-3,10_000 });
        List<ComplexStainLabware> csls = IntStream.range(0, runs.length)
                .mapToObj(i -> new ComplexStainLabware("STAN-"+i, "000-0000", runs[i], null, null, null, null))
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
        String[] bondBarcodes = (valid? new String[] { "00000000", "00000000", "099A1AZZ", "123A"}
                : new String[] { null, "12340ABC", "0000000%", "0000000%", "110", "222202222" });
        List<ComplexStainLabware> csls = IntStream.range(0, bondBarcodes.length)
                .mapToObj(i -> new ComplexStainLabware("STAN-"+i, bondBarcodes[i], 1, null, null, null, null))
                .collect(toList());
        List<String> problems = new ArrayList<>(valid ? 0 : 1);
        service.validateBondBarcodes(problems, csls);
        if (valid) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactly("Bond barcodes not of the expected format: [null, 0000000%, 110, 222202222]");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testValidatePanels(boolean missing) {
        List<ComplexStainLabware> csls = IntStream.range(0, 3).mapToObj(i -> new ComplexStainLabware())
                .collect(toList());
        csls.get(0).setPanel(StainPanel.negative);
        if (!missing) {
            csls.get(1).setPanel(StainPanel.positive);
            csls.get(2).setPanel(StainPanel.marker);
        }
        List<String> problems = new ArrayList<>(missing ? 1 : 0);
        service.validatePanels(problems, csls);
        if (!missing) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactly("Experiment panel must be specified for each labware.");
        }
    }

    @ParameterizedTest
    @MethodSource("validatePlexesArgs")
    public void testValidatePlexes(List<StainType> stainTypes, List<ComplexStainLabware> csls,
                                   List<String> expectedProblems) {
        final List<String> problems = new ArrayList<>(expectedProblems.size());
        service.validatePlexes(problems, stainTypes, csls);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> validatePlexesArgs() {
        StainType rna = new StainType(3, "RNAscope");
        StainType ihc = new StainType(4, "IHC");
        ComplexStainLabware cslr = new ComplexStainLabware(null, null, 0, null, 16, null, null);
        ComplexStainLabware csli = new ComplexStainLabware(null, null, 0, null, null, 15, null);
        ComplexStainLabware csl0 = new ComplexStainLabware();
        ComplexStainLabware cslb = new ComplexStainLabware(null, null, 0, null, 1, 100, null);
        ComplexStainLabware cslrx = new ComplexStainLabware(null, null, 0, null, 0, null, null);
        ComplexStainLabware cslix = new ComplexStainLabware(null, null, 0, null, null, 0, null);
        ComplexStainLabware cslbx = new ComplexStainLabware(null, null, 0, null, 101, 101, null);

        final String rnaMissing = "RNAscope plex number is required for RNAscope stain.";
        final String rnaUnexpected = "RNAscope plex number is not expected for non-RNAscope stain.";
        final String ihcMissing = "IHC plex number is required for IHC stain.";
        final String ihcUnexpected = "IHC plex number is not expected for non-IHC stain.";
        final String badRange = "Plex number is expected to be in the range 1 to 100.";
        return Arrays.stream(new Object[][] {
                { rna, cslr },
                { rna, csl0, rnaMissing},
                { rna, cslb, ihcUnexpected},
                { rna, csli, rnaMissing, ihcUnexpected},
                { rna, csl0, cslb, rnaMissing, ihcUnexpected},
                { rna, cslrx, badRange},
                { rna, cslix, rnaMissing, ihcUnexpected, badRange},

                {ihc, csli},
                {ihc, csl0, ihcMissing},
                {ihc, cslb, rnaUnexpected},
                {ihc, cslr, ihcMissing, rnaUnexpected},
                {ihc, csl0, cslb, ihcMissing, rnaUnexpected},
                {ihc, cslix, badRange},
                {ihc, cslrx, ihcMissing, rnaUnexpected, badRange},

                {cslb, rnaUnexpected, ihcUnexpected},
                {cslix, cslrx, rnaUnexpected, ihcUnexpected, badRange},
                {cslbx, rnaUnexpected, ihcUnexpected, badRange},

                {rna, ihc, cslb},
                {rna, ihc, csli, rnaMissing},
                {rna, ihc, cslr, ihcMissing},
                {rna, ihc, cslb, csl0, rnaMissing, ihcMissing},
                {rna, ihc, cslbx, badRange},
        }).map(arr -> {
            List<StainType> stainTypes = new ArrayList<>();
            List<ComplexStainLabware> csls = new ArrayList<>();
            List<String> problems = new ArrayList<>();
            for (Object v : arr) {
                if (v instanceof StainType) {
                    stainTypes.add((StainType) v);
                } else if (v instanceof ComplexStainLabware) {
                    csls.add((ComplexStainLabware) v);
                } else if (v instanceof String) {
                    problems.add((String) v);
                }
            }
            return Arguments.of(stainTypes, csls, problems);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testLoadLabware(boolean valid) {
        String[] barcodes = new String[] {"STAN-1", "STAN-2", "STAN-1", null};
        List<ComplexStainLabware> csls = Arrays.stream(barcodes)
                .map(bc -> new ComplexStainLabware(bc, "0000-000", 1, null, null, null, null))
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

    @Test
    public void testRecord() {
        User user = EntityFactory.getUser();
        OperationType opType = EntityFactory.makeOperationType("Stain", null, OperationTypeFlag.IN_PLACE, OperationTypeFlag.STAIN);
        List<StainType> stainTypes = List.of(new StainType(10, "RNAscope"));
        Work work1 = new Work(1, "SGP1", null, null, null, null, Work.Status.active);
        Work work2 = new Work(2, "SGP2", null, null, null, null, Work.Status.active);
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, work1, work2);
        LabwareType lt = EntityFactory.getTubeType();
        Labware[] lws = IntStream.range(0,4)
                .mapToObj(i -> EntityFactory.makeEmptyLabware(lt))
                .toArray(Labware[]::new);
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lws);
        List<ComplexStainLabware> csls = List.of(
                new ComplexStainLabware(lws[0].getBarcode(), "0000-000", 1, work1.getWorkNumber(), null, null, null),
                new ComplexStainLabware(lws[1].getBarcode(), "0000-001", 2, work1.getWorkNumber(), null, null, null),
                new ComplexStainLabware(lws[2].getBarcode(), "0000-002", 3, work2.getWorkNumber(), null, null, null),
                new ComplexStainLabware(lws[3].getBarcode(), "0000-003", 3, work2.getWorkNumber(), null, null, null)
        );
        ComplexStainRequest request = new ComplexStainRequest(List.of("RNAscope"), csls);

        Operation[] ops = IntStream.range(0, lws.length)
                .mapToObj(i -> {
                    Operation op = new Operation(200+i, opType, null, null, user);
                    doReturn(op).when(service).createOp(user, lws[i], opType, stainTypes);
                    return op;
                })
                .toArray(Operation[]::new);
        doNothing().when(service).recordLabwareNotes(any(), anyInt(), anyInt());
        when(mockWorkService.link(any(Work.class), any())).then(Matchers.returnArgument());

        OperationResult opres = service.record(user, request, opType, stainTypes, lwMap, workMap);
        assertThat(opres.getLabware()).containsExactly(lws);
        assertThat(opres.getOperations()).containsExactly(ops);

        for (int i = 0; i < lws.length; ++i) {
            Labware lw = lws[i];
            Operation op = ops[i];
            verify(service).createOp(user, lw, opType, stainTypes);
            verify(service).recordLabwareNotes(csls.get(i), lw.getId(), op.getId());
        }

        verify(mockWorkService).link(work1, List.of(ops[0], ops[1]));
        verify(mockWorkService).link(work2, List.of(ops[2], ops[3]));
        verifyNoMoreInteractions(mockWorkService);
    }

    @Test
    public void testCreateOp() {
        User user = EntityFactory.getUser();
        OperationType opType = EntityFactory.makeOperationType("Stain", null);
        Labware lw = EntityFactory.getTube();
        final int opId = 40;
        when(mockOpService.createOperationInPlace(same(opType), same(user), same(lw), isNull(), any())).thenAnswer(invocation -> {
            Operation op = new Operation();
            op.setId(opId);
            return op;
        });

        final List<StainType> stainTypes = List.of(
                new StainType(4, "RNAscope"),
                new StainType(5, "Bananas")
        );
        Operation op = service.createOp(user, lw, opType, stainTypes);
        assertEquals(opId, op.getId());
        verify(mockStainTypeRepo).saveOperationStainTypes(opId, stainTypes);
    }

    @ParameterizedTest
    @ValueSource(strings={"RNAscope", "RNAscope/IHC", "IHC"})
    public void testRecordLabwareNotes(String stainNames) {
        boolean rna = stainNames.contains("RNAscope");
        boolean ihc = stainNames.contains("IHC");

        ComplexStainLabware csl = new ComplexStainLabware("STAN-01", "1234 ABC", 23, null,
                rna ? 18 : null, ihc ? 17 : null, StainPanel.negative);

        final int lwId = 200;
        final int opId = 400;
        service.recordLabwareNotes(csl, lwId, opId);
        List<LabwareNote> expectedNotes = new ArrayList<>(5);
        if (ihc) {
            expectedNotes.add(new LabwareNote(null, lwId, opId, ComplexStainServiceImp.LW_NOTE_PLEX_IHC, "17"));
        }
        if (rna) {
            expectedNotes.add(new LabwareNote(null, lwId, opId, ComplexStainServiceImp.LW_NOTE_PLEX_RNASCOPE, "18"));
        }
        expectedNotes.add(new LabwareNote(null, lwId, opId, ComplexStainServiceImp.LW_NOTE_PANEL, "negative"));
        expectedNotes.add(new LabwareNote(null, lwId, opId, ComplexStainServiceImp.LW_NOTE_BOND_BARCODE, csl.getBondBarcode()));
        expectedNotes.add(new LabwareNote(null, lwId, opId, ComplexStainServiceImp.LW_NOTE_BOND_RUN, "23"));
        verify(mockLwNoteRepo).saveAll(Matchers.sameElements(expectedNotes));
    }
}
