package uk.ac.sanger.sccp.stan.service.register;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.register.*;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.register.OriginalSampleRegisterServiceImp.DataStruct;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.stan.service.work.WorkService.WorkOp;
import uk.ac.sanger.sccp.utils.UCMap;
import uk.ac.sanger.sccp.utils.Zip;

import java.time.LocalDate;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.*;

/**
 * Tests {@link OriginalSampleRegisterServiceImp}
 */
public class TestOriginalSampleRegisterService {
    @Mock
    private DonorRepo mockDonorRepo;
    @Mock
    private TissueRepo mockTissueRepo;
    @Mock
    private TissueTypeRepo mockTissueTypeRepo;
    @Mock
    private SampleRepo mockSampleRepo;
    @Mock
    private BioStateRepo mockBsRepo;
    @Mock
    private SlotRepo mockSlotRepo;
    @Mock
    private HmdmcRepo mockHmdmcRepo;
    @Mock
    private SpeciesRepo mockSpeciesRepo;
    @Mock
    private FixativeRepo mockFixativeRepo;
    @Mock
    private MediumRepo mockMediumRepo;
    @Mock
    private SolutionRepo mockSolutionRepo;
    @Mock
    private LabwareTypeRepo mockLtRepo;
    @Mock
    private OperationTypeRepo mockOpTypeRepo;
    @Mock
    private OperationSolutionRepo mockOpSolRepo;
    @Mock
    private CellClassRepo mockCellClassRepo;
    @Mock
    private Validator<String> mockDonorNameValidator;
    @Mock
    private Validator<String> mockExternalNameValidator;
    @Mock
    private Validator<String> mockHmdmcValidator;
    @Mock
    private Validator<String> mockReplicateValidator;
    @Mock
    private LabwareService mockLabwareService;
    @Mock
    private OperationService mockOpService;
    @Mock
    private WorkService mockWorkService;
    @Mock
    private BioRiskService mockBioRiskService;

    OriginalSampleRegisterServiceImp service;

    private AutoCloseable mocking;


    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
        service = spy(new OriginalSampleRegisterServiceImp(mockDonorRepo, mockTissueRepo, mockTissueTypeRepo,
                mockSampleRepo, mockBsRepo, mockSlotRepo, mockHmdmcRepo, mockSpeciesRepo, mockFixativeRepo,
                mockMediumRepo, mockSolutionRepo, mockLtRepo, mockOpTypeRepo, mockOpSolRepo, mockCellClassRepo,
                mockDonorNameValidator, mockExternalNameValidator,
                mockHmdmcValidator, mockReplicateValidator, mockLabwareService, mockOpService, mockWorkService,
                mockBioRiskService));
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
    }

    @Test
    public void testRegister_noSamples() {
        User user = EntityFactory.getUser();
        OriginalSampleRegisterRequest request = new OriginalSampleRegisterRequest();
        assertValidationException(() -> service.register(user, request), "The request validation failed.",
                "No samples specified in request.");
        verifyNoInteractions(mockDonorRepo);
        verifyNoInteractions(mockTissueRepo);
        verifyNoInteractions(mockSampleRepo);
        verifyNoInteractions(mockLabwareService);
        verifyNoInteractions(mockOpService);
    }

    @Test
    public void testRegister_validationErrors() {
        OriginalSampleData data = new OriginalSampleData("DONOR1", LifeStage.adult, "HMDMC1", "TISSUE1", 5,
                "R1", "EXT1", null, "LT1", "SOL1", "FIX1", "SPEC1", LocalDate.of(2022,1,1), "TODO", "RISK1", "Tissue");
        OriginalSampleRegisterRequest request = new OriginalSampleRegisterRequest(List.of(data));

        doAnswer(invocation -> {
            Collection<String> problems = invocation.getArgument(0);
            OriginalSampleRegisterRequest req = invocation.getArgument(1);
            String fieldName = invocation.getArgument(2);
            Function<OriginalSampleData, ?> func = invocation.getArgument(3);

            problems.add(fieldName+" wrong: "+func.apply(req.getSamples().getFirst()));
            return null;
        }).when(service).checkFormat(any(), any(), any(), any(), anyBoolean(), any());

        doNothing().when(service).checkHmdmcsForSpecies(any(), any());
        doNothing().when(service).checkCollectionDates(any(), any());
        doNothing().when(service).checkExistence(any(), any(), any(), any(), any(), any());

        doNothing().when(service).checkWorks(any(), any());
        doNothing().when(service).loadDonors(any());
        doNothing().when(service).checkTissueTypesAndSpatialLocations(any(), any());

        doNothing().when(service).checkExternalNamesUnique(any(), any());
        doNothing().when(service).checkDonorFieldsAreConsistent(any(), any());
        doNothing().when(service).checkBioRisks(any(), any());

        User user = EntityFactory.getUser();

        assertValidationException(() -> service.register(user, request), "The request validation failed.",
                "Donor identifier wrong: DONOR1",
                "External identifier wrong: EXT1",
                "Life stage wrong: adult",
                "HuMFre number wrong: HMDMC1",
                "Replicate number wrong: R1",
                "Species wrong: SPEC1",
                "Tissue type wrong: TISSUE1",
                "Spatial location wrong: 5",
                "Fixative wrong: FIX1",
                "Solution wrong: SOL1",
                "Labware type wrong: LT1");

        verifyValidationMethods(request, null);

        verify(service, never()).createNewDonors(any());
        verify(service, never()).createNewSamples(any());
        verify(service, never()).createNewLabware(any());
        verify(service, never()).recordRegistrations(any(), any());
        verify(service, never()).recordSolutions(any());
        verify(service, never()).linkWork(any());
        verify(service, never()).makeResult(any());
    }

    @Test
    public void testRegister_valid() {
        User user = EntityFactory.getUser();
        OriginalSampleData data = new OriginalSampleData("DONOR1", LifeStage.adult, "HMDMC1", "TISSUE1", 5,
                "R1", "EXT1", null, "LT1", "SOL1", "FIX1", "SPEC1", LocalDate.of(2022,1,1), "TODO", "risk1", "Tissue");
        OriginalSampleRegisterRequest request = new OriginalSampleRegisterRequest(List.of(data));
        doNothing().when(service).checkFormat(any(), any(), any(), any(), anyBoolean(), any());
        doNothing().when(service).checkHmdmcsForSpecies(any(), any());
        doNothing().when(service).checkCollectionDates(any(), any());
        doNothing().when(service).checkBioRisks(any(), any());

        doNothing().when(service).checkExistence(any(), any(), any(), any(), any(), any());

        doNothing().when(service).checkWorks(any(), any());
        doNothing().when(service).loadDonors(any());
        doNothing().when(service).checkExternalNamesUnique(any(), any());
        doNothing().when(service).checkDonorFieldsAreConsistent(any(), any());
        doNothing().when(service).checkTissueTypesAndSpatialLocations(any(), any());

        doNothing().when(service).createNewDonors(any());
        doNothing().when(service).createNewSamples(any());
        doNothing().when(service).createNewLabware(any());
        doNothing().when(service).recordRegistrations(any(), any());
        doNothing().when(service).recordSolutions(any());
        doNothing().when(service).linkBioRisks(any());
        List<List<DataStruct>> groups = List.of(List.of(new DataStruct(data)));
        doReturn(groups).when(service).compilePotGroups(any());
        final RegisterResult expectedResult = new RegisterResult(List.of(EntityFactory.getTube()));
        doReturn(expectedResult).when(service).makeResult(any());

        assertSame(expectedResult, service.register(user, request));
        var datas = verifyValidationMethods(request, groups);

        verify(service).createNewDonors(same(datas));
        verify(service).createNewSamples(same(datas));
        verify(service).createNewLabware(same(groups));
        verify(service).recordRegistrations(same(user), same(groups));
        verify(service).recordSolutions(same(groups));
        verify(service).linkWork(same(groups));
        verify(service).linkBioRisks(same(groups));
        verify(service).makeResult(same(groups));
    }

    private List<DataStruct> verifyValidationMethods(OriginalSampleRegisterRequest request, List<List<DataStruct>> groups) {
        ArgumentCaptor<Collection<String>> problemsArgCaptor = genericCaptor(Collection.class);
        verify(service).checkFormat(problemsArgCaptor.capture(), same(request), eq("Donor identifier"), any(), eq(true), same(mockDonorNameValidator));
        Collection<String> problems = problemsArgCaptor.getValue();
        verify(service).checkFormat(same(problems), same(request), eq("External identifier"), any(), eq(false), same(mockExternalNameValidator));
        verify(service).checkFormat(same(problems), same(request), eq("Life stage"), any(), eq(false), isNull());
        verify(service).checkFormat(same(problems), same(request), eq("HuMFre number"), any(), eq(false), same(mockHmdmcValidator));
        verify(service).checkFormat(same(problems), same(request), eq("Replicate number"), any(), eq(false), same(mockReplicateValidator));
        verify(service).checkFormat(same(problems), same(request), eq("Species"), any(), eq(true), isNull());
        verify(service).checkFormat(same(problems), same(request), eq("Tissue type"), any(), eq(true), isNull());
        verify(service).checkFormat(same(problems), same(request), eq("Spatial location"), any(), eq(true), isNull());
        verify(service).checkFormat(same(problems), same(request), eq("Fixative"), any(), eq(true), isNull());
        verify(service).checkFormat(same(problems), same(request), eq("Solution"), any(), eq(true), isNull());
        verify(service).checkFormat(same(problems), same(request), eq("Labware type"), any(), eq(true), isNull());

        ArgumentCaptor<List<DataStruct>> dataStructArgCaptor = genericCaptor(List.class);

        verify(service).checkCollectionDates(same(problems), same(request));
        verify(service).checkExistence(same(problems), dataStructArgCaptor.capture(), eq("HuMFre number"), any(), any(), any());
        List<DataStruct> datas = dataStructArgCaptor.getValue();
        verify(service).checkHmdmcsForSpecies(same(problems), same(datas));
        verify(service).checkExistence(same(problems), same(datas), eq("species"), any(), any(), any());
        verify(service).checkExistence(same(problems), same(datas), eq("fixative"), any(), any(), any());
        verify(service).checkExistence(same(problems), same(datas), eq("solution"), any(), any(), any());
        verify(service).checkExistence(same(problems), same(datas), eq("labware type"), any(), any(), any());
        verify(service).checkWorks(same(problems), same(datas));
        verify(service).loadDonors(same(datas));
        verify(service).checkDonorFieldsAreConsistent(same(problems), same(datas));

        verify(service).checkTissueTypesAndSpatialLocations(same(problems), same(datas));
        verify(service).checkBioRisks(same(problems), same(datas));
        if (groups != null) {
            verify(service).compilePotGroups(same(datas));
            verify(service).checkPotGroups(same(problems), same(groups));
        }
        return datas;
    }

    @ParameterizedTest
    @CsvSource({
            "true,false,false,true,false",
            "false,true,true,false,true",
            "true,true,false,false,false",
            "true,false,true,false,false",
            "true,true,false,true,true",
    })
    public void testCheckFormat(boolean required, boolean anyMissing, boolean anyEmpty, boolean hasValidator, boolean anyInvalid) {
        final Function<OriginalSampleData, String> func = OriginalSampleData::getDonorIdentifier;
        List<OriginalSampleData> data = new ArrayList<>(3);
        data.add(osdWithDonor("DONOR1"));
        if (anyMissing) {
            data.add(osdWithDonor(null));
        }
        if (anyEmpty) {
            data.add(osdWithDonor(""));
        }
        if (anyInvalid) {
            data.add(osdWithDonor("DONOR!"));
        }
        Validator<String> validator;
        if (hasValidator) {
            validator = (string, con) -> {
                if (string.indexOf('!')>=0) {
                    con.accept("Bad: "+string);
                    return false;
                }
                return true;
            };
        } else {
            validator = null;
        }

        List<String> expectedProblems = new ArrayList<>(2);
        if (required && (anyMissing || anyEmpty)) {
            expectedProblems.add("FIELD missing.");
        }
        if (hasValidator && anyInvalid) {
            expectedProblems.add("Bad: DONOR!");
        }
        List<String> problems = new ArrayList<>(expectedProblems.size());
        service.checkFormat(problems, new OriginalSampleRegisterRequest(data), "FIELD", func, required, validator);

        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testCheckExistence(boolean anyUnknown) {
        String[] names = { "FIX1", "FIX2", "fix1", null};
        if (anyUnknown) {
            names[3] = "FIXX";
        }
        List<DataStruct> datas = Arrays.stream(names)
                .map(name -> {
                    OriginalSampleData osd = new OriginalSampleData();
                    osd.setFixative(name);
                    return new DataStruct(osd);
                })
                .collect(toList());
        Function<OriginalSampleData, String> fieldFunc = OriginalSampleData::getFixative;
        BiConsumer<DataStruct, Fixative> setter = DataStruct::setFixative;

        List<Fixative> fixatives = List.of(new Fixative(1, "fix1"), new Fixative(2, "fix2"));
        Function<String, Optional<Fixative>> repoFunc = string -> fixatives.stream()
                .filter(f -> f.getName().equalsIgnoreCase(string))
                .findAny();
        List<String> expectedProblems;
        if (anyUnknown) {
            expectedProblems = List.of("Unknown FIELD: [FIXX]");
        } else {
            expectedProblems = List.of();
        }
        List<String> problems = new ArrayList<>(expectedProblems.size());
        service.checkExistence(problems, datas, "FIELD", fieldFunc, repoFunc, setter);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
        assertSame(fixatives.get(0), datas.get(0).fixative);
        assertSame(fixatives.get(1), datas.get(1).fixative);
        assertSame(fixatives.get(0), datas.get(2).fixative);
        assertNull(datas.get(3).fixative);
    }

    @ParameterizedTest
    @MethodSource("checkCollectionDatesArgs")
    public void testCheckCollectionDates(OriginalSampleRegisterRequest request, Collection<String> expectedProblems) {
        Set<String> problems = new HashSet<>(expectedProblems.size());
        service.checkCollectionDates(problems, request);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> checkCollectionDatesArgs() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        return Arrays.stream(new Object[][] {
                {
                        osd(LifeStage.fetal, Species.HUMAN_NAME, yesterday),
                        osd(LifeStage.adult, Species.HUMAN_NAME, yesterday),
                        osd(LifeStage.paediatric, "Hamster", null),
                        osd(LifeStage.adult, Species.HUMAN_NAME, null),
                },
                {
                    osd(LifeStage.adult, "Hamster", tomorrow),
                        "Collection date must be in the past.",
                },
                {
                    osd(LifeStage.fetal, Species.HUMAN_NAME, null),
                        "Collection date is required for fetal samples.",
                },
                {
                    osd(LifeStage.fetal, Species.HUMAN_NAME, null),
                        osd(LifeStage.adult, "Hamster", tomorrow),
                        "Collection date must be in the past.",
                        "Collection date is required for fetal samples.",
                },
        }).map(arr -> {
            List<String> problems = typeFilterToList(arr, String.class);
            List<OriginalSampleData> data = typeFilterToList(arr, OriginalSampleData.class);
            return Arguments.of(new OriginalSampleRegisterRequest(data), problems);
        });
    }

    @ParameterizedTest
    @MethodSource("checkDonorFieldsArgs")
    void testCheckDonorFieldsAreConsistent(List<DataStruct> datas,
                                                  Collection<String> expectedProblems) {
        List<String> problems = new ArrayList<>(expectedProblems.size());
        service.checkDonorFieldsAreConsistent(problems, datas);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> checkDonorFieldsArgs() {
        Species human = new Species(1, Species.HUMAN_NAME);
        Species banana = new Species(2, "Banana");
        Donor donor1 = new Donor(1, "DONOR1", LifeStage.adult, human);
        Donor donor2 = new Donor(2, "DONOR2", LifeStage.fetal, banana);
        UCMap<Donor> donors = UCMap.from(Donor::getDonorName, donor1, donor2);
        OriginalSampleData correct1 = osdWithDonor(donor1.getDonorName(), LifeStage.adult, Species.HUMAN_NAME.toLowerCase());
        OriginalSampleData correct2 = osdWithDonor(donor2.getDonorName(), LifeStage.fetal, "banana");
        OriginalSampleData new1A = osdWithDonor("DONOR3", LifeStage.adult, Species.HUMAN_NAME);
        OriginalSampleData new1B = osdWithDonor("DONOR3", LifeStage.fetal, Species.HUMAN_NAME);
        OriginalSampleData new2A = osdWithDonor("DONOR4", LifeStage.adult, Species.HUMAN_NAME);
        OriginalSampleData new2B = osdWithDonor("DONOR4", LifeStage.adult, "Banana");
        OriginalSampleData missing1 = osdWithDonor("DONOR1", null, Species.HUMAN_NAME);
        OriginalSampleData missing2 = osdWithDonor("DONOR2", LifeStage.fetal, null);
        OriginalSampleData missing3 = osdWithDonor(null, LifeStage.fetal, Species.HUMAN_NAME);
        OriginalSampleData wrong1 = osdWithDonor("donor1", LifeStage.paediatric, Species.HUMAN_NAME);
        OriginalSampleData wrong2 = osdWithDonor("donor2", LifeStage.fetal, "carrot");

        return Arrays.stream(new Object[][] {
                { correct1, correct2, new1A, new2A, missing1, missing2, missing3 },
                { new1A, new1B, "Multiple life stages specified for donor DONOR3." },
                { new2A, new2B, "Multiple species specified for donor DONOR4." },
                { wrong1, "Donor life stage inconsistent with existing donor DONOR1."},
                { wrong2, "Donor species inconsistent with existing donor DONOR2."},
                { correct1, missing1, new1A, new1B, new2A, new2B, wrong1, wrong2,
                        "Donor life stage inconsistent with existing donor DONOR1.",
                        "Donor species inconsistent with existing donor DONOR2.",
                        "Multiple life stages specified for donor DONOR3.",
                        "Multiple species specified for donor DONOR4.",
                },
        }).map(arr -> {
            List<OriginalSampleData> osds = typeFilterToList(arr, OriginalSampleData.class);
            List<DataStruct> datas = osds.stream()
                    .map(DataStruct::new)
                    .peek(ds -> ds.donor = donors.get(ds.getOriginalSampleData().getDonorIdentifier()))
                    .collect(toList());
            List<String> problems = typeFilterToList(arr, String.class);
            return Arguments.of(datas, problems);
        });
    }

    @ParameterizedTest
    @MethodSource("checkHmdmcsArgs")
    public void testCheckHmdmcsForSpecies(Species species, CellClass cc, OriginalSampleData osd, String expectedProblem) {
        List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        DataStruct data = new DataStruct(osd);
        data.species =species;
        data.cellClass = cc;
        service.checkHmdmcsForSpecies(problems, List.of(data));
        assertProblem(problems, expectedProblem);
    }

    static Stream<Arguments> checkHmdmcsArgs() {
        Species human = new Species(1, Species.HUMAN_NAME);
        Species cat = new Species(2, "Cat");
        CellClass tissue = new CellClass(1, "Tissue", true, true);
        CellClass cake = new CellClass(2, "Cake", false, true);
        OriginalSampleData withHmdmc = osdWithHmdmc("HMDMC1");
        OriginalSampleData withoutHmdmc = osdWithHmdmc("");
        final String HMDMC_MISSING = "HuMFre number missing for human tissue samples.";
        final String HMDMC_UNEXPECTED = "HuMFre number not expected for non-human samples.";
        return Arrays.stream(new Object[][]{
                {human, tissue, withHmdmc, null},
                {human, cake, withHmdmc, null},
                {human, tissue, withoutHmdmc, HMDMC_MISSING},
                {human, cake, withoutHmdmc, null},
                {cat, tissue, withHmdmc, HMDMC_UNEXPECTED},
                {cat, cake, withHmdmc, HMDMC_UNEXPECTED},
                {cat, tissue, withoutHmdmc, null},
                {cat, cake, withoutHmdmc, null},
                {null, null, withoutHmdmc, null},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("checkExternalNamesUniqueArgs")
    public void testCheckExternalNamesUnique(List<Tissue> tissues, Collection<String> extNames,
                                             Collection<String> expectedProblems) {
        when(mockTissueRepo.findAllByExternalNameIn(any())).thenReturn(tissues);

        List<String> problems = new ArrayList<>(expectedProblems.size());
        OriginalSampleRegisterRequest request = new OriginalSampleRegisterRequest(
                extNames.stream()
                        .map(xn -> {
                            OriginalSampleData data = new OriginalSampleData();
                            data.setExternalIdentifier(xn);
                            return data;
                        })
                        .collect(toList())
        );
        service.checkExternalNamesUnique(problems, request);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
        Set<String> expectedNames = extNames.stream()
                .filter(xn -> xn!=null && !xn.isEmpty())
                .map(String::toUpperCase)
                .collect(toSet());
        if (expectedNames.isEmpty()) {
            verifyNoInteractions(mockTissueRepo);
        } else {
            verify(mockTissueRepo).findAllByExternalNameIn(expectedNames);
        }
    }

    static Stream<Arguments> checkExternalNamesUniqueArgs() {
        Tissue tissue = new Tissue(1, "EXT1", null, null, null, null, null, null, null, null, null);
        String name1 = tissue.getExternalName();
        List<Tissue> tissues = List.of(tissue);
        return Arrays.stream(new Object[][] {
                { tissues, List.of(name1, "EXTX"), List.of("External name already used: [EXT1]") },
                { List.of(), List.of("extx", "EXTX", "exty", "EXTZ", "EXTY"), List.of("External names repeated: [EXTX, EXTY]")},
                { List.of(), List.of("EXTX", "EXTZ"), List.of() },
                { tissues, List.of(name1, "extx", "EXTX"), List.of("External name already used: [EXT1]", "External names repeated: [EXTX]")},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("getSpatialLocationArgs")
    public void testGetSpatialLocation(TissueType tissueType, Integer slCode, SpatialLocation expected) {
        assertSame(expected, service.getSpatialLocation(tissueType, slCode));
    }

    static Stream<Arguments> getSpatialLocationArgs() {
        TissueType tt = new TissueType(1, "ttype", "tty");
        SpatialLocation sl0 = new SpatialLocation(1, "Unknown", 0, tt);
        SpatialLocation sl1 = new SpatialLocation(2, "Hair", 1, tt);
        tt.setSpatialLocations(List.of(sl0, sl1));

        return Arrays.stream(new Object[][] {
                {tt, 0, sl0},
                {tt, 1, sl1},
                {tt, 2, null},
                {tt, -1, null},
                {tt, null, null},
                {null, 1, null},
                {null, null, null},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("checkTissueTypesAndSpatialLocationsArgs")
    void testCheckTissueTypesAndSpatialLocations(List<DataStruct> datas,
                                                        List<SpatialLocation> expectedSLs,
                                                        List<TissueType> allTissueTypes,
                                                        List<String> expectedProblems) {
        when(mockTissueTypeRepo.findAllByNameIn(any())).thenReturn(allTissueTypes);
        List<String> problems = new ArrayList<>(expectedProblems.size());
        service.checkTissueTypesAndSpatialLocations(problems, datas);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
        for (int i = 0; i < datas.size(); ++i) {
            assertSame(expectedSLs.get(i), datas.get(i).spatialLocation);
        }
        Set<String> tissueNamesToLookUp = new HashSet<>();
        for (var data : datas) {
            final String ttName = data.getOriginalSampleData().getTissueType();
            if (ttName !=null && !ttName.isEmpty()) {
                tissueNamesToLookUp.add(ttName.toUpperCase());
            }
        }
        if (tissueNamesToLookUp.isEmpty()) {
            verifyNoInteractions(mockTissueTypeRepo);
        } else {
            verify(mockTissueTypeRepo).findAllByNameIn(tissueNamesToLookUp);
        }
    }

    static Stream<Arguments> checkTissueTypesAndSpatialLocationsArgs() {
        final TissueType arm = new TissueType(1, "Arm", "ARM");
        final TissueType leg = new TissueType(2, "Leg", "LEG");
        final TissueType tail = new TissueType(3, "Tail", "TAIL");
        tail.setEnabled(false);
        for (TissueType tt : List.of(arm, leg, tail)) {
            List<SpatialLocation> sls = List.of(
                    new SpatialLocation(10*tt.getId(), "Unknown", 0, tt),
                    new SpatialLocation(10*tt.getId()+1, "Alpha", 1, tt)
            );
            tt.setSpatialLocations(sls);
        }
        final SpatialLocation arm0 = arm.getSpatialLocations().get(0);
        final SpatialLocation arm1 = arm.getSpatialLocations().get(1);
        final SpatialLocation leg0 = leg.getSpatialLocations().getFirst();
        final SpatialLocation leg1 = leg.getSpatialLocations().getLast();
        leg1.setEnabled(false);

        return Arrays.stream(new Object[][] {
                { osdWithSL("Arm", 0), arm0,
                        osdWithSL("Arm", 1), arm1,
                        osdWithSL("LEG", 0), leg0,
                  arm, leg },
                { osdWithSL("Arm", 0), arm0,
                        osdWithSL("Flarm", 0), null,
                        osdWithSL("Floop", null), null,
                  arm, "Unknown tissue type: [\"Flarm\", \"Floop\"]" },
                { osdWithSL("ARM", 3), null,
                        osdWithSL("Leg", -1), null,
                        osdWithSL("ARM", 1), arm1,
                  arm, leg, "There is no spatial location 3 for tissue type Arm.", "There is no spatial location -1 for tissue type Leg." },
                { osdWithSL("arm", 0), arm0,
                        osdWithSL("arm", 17), null,
                        osdWithSL("Floop", null), null,
                  arm, "There is no spatial location 17 for tissue type Arm.", "Unknown tissue type: [\"Floop\"]"},
                { osdWithSL("tail", 0), tail.getSpatialLocations().getFirst(),
                        tail, "Disabled tissue type: [Tail]"},
                { osdWithSL("leg", 1), leg1,
                        leg, "Spatial location 1 for tissue type Leg is disabled."},
        }).map(arr -> {
            List<DataStruct> datas = typeFilter(Arrays.stream(arr), OriginalSampleData.class)
                    .map(DataStruct::new)
                    .collect(toList());
            List<SpatialLocation> expectedSLs = Arrays.stream(arr)
                    .filter(x -> x==null || x instanceof SpatialLocation)
                    .map(x -> (SpatialLocation) x)
                    .collect(toList());
            List<TissueType> tissueTypes = typeFilterToList(arr, TissueType.class);
            List<String> expectedProblems = typeFilterToList(arr, String.class);
            return Arguments.of(datas, expectedSLs, tissueTypes, expectedProblems);
        });
    }

    @Test
    public void testCheckWorks_none() {
        List<DataStruct> datas = Stream.of("", null)
                .map(wn -> new DataStruct(osdWithWorkNumber(wn)))
                .collect(toList());
        final List<String> problems = new ArrayList<>(0);
        service.checkWorks(problems, datas);
        datas.forEach(d -> assertNull(d.work));
        verifyNoInteractions(mockWorkService);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testCheckWorks_ok(boolean errors) {
        List<DataStruct> datas = Stream.of("", "SGP1", "SGP2", "SGP1")
                .map(wn -> new DataStruct(osdWithWorkNumber(wn)))
                .collect(toList());
        final List<String> problems = new ArrayList<>(errors ? 1 : 0);
        Work[] works = IntStream.range(1,3)
                .mapToObj(i -> {
                    Work work = new Work();
                    work.setId(i);
                    work.setWorkNumber("SGP"+i);
                    return work;
                })
                .toArray(Work[]::new);
        String expectedProblem = errors ? "Bad work stuff." : null;
        UCMap<Work> workMap = UCMap.from(Work::getWorkNumber, works);
        mayAddProblem(expectedProblem, workMap).when(mockWorkService).validateUsableWorks(any(), any());
        service.checkWorks(problems, datas);
        assertProblem(problems, expectedProblem);
        Work[] expectedDataWork = { null, works[0], works[1], works[0] };
        for (int i = 0; i < datas.size(); ++i) {
            assertSame(expectedDataWork[i], datas.get(i).work);
        }
        verify(mockWorkService).validateUsableWorks(any(), eq(Set.of("SGP1", "SGP2")));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCheckBioRisks() {
        BioRisk risk1 = new BioRisk(1, "risk1");
        BioRisk risk2 = new BioRisk(2, "risk2");
        UCMap<BioRisk> riskMap = UCMap.from(BioRisk::getCode, risk1, risk2);
        List<DataStruct> datas = Stream.of("RISK1","", "risk2")
                .map(code -> {
                    OriginalSampleData osd = new OriginalSampleData();
                    osd.setBioRiskCode(code);
                    return new DataStruct(osd);
                })
                .toList();
        when(mockBioRiskService.loadAndValidateBioRisks(any(), any(), any(), any())).thenReturn(riskMap);

        ArgumentCaptor<Function<OriginalSampleData, String>> getterCaptor = ArgumentCaptor.forClass(Function.class);
        ArgumentCaptor<BiConsumer<OriginalSampleData, String>> setterCaptor = ArgumentCaptor.forClass(BiConsumer.class);
        ArgumentCaptor<Stream<OriginalSampleData>> streamCaptor = ArgumentCaptor.forClass(Stream.class);
        final List<String> problems = new ArrayList<>();
        service.checkBioRisks(problems, datas);
        verify(mockBioRiskService).loadAndValidateBioRisks(same(problems), streamCaptor.capture(), getterCaptor.capture(), setterCaptor.capture());
        // Test the correct stream and getter were passed
        assertThat(streamCaptor.getValue().map(getterCaptor.getValue())).containsExactly("RISK1","", "risk2");
        OriginalSampleData osd = new OriginalSampleData();
        // Test the correct setter was passed
        setterCaptor.getValue().accept(osd, "Banana");
        assertEquals("Banana", osd.getBioRiskCode());
        assertThat(datas.stream().map(data -> data.bioRisk)).containsExactly(risk1, null, risk2);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "-,",
            "2,",
            "3 3 4,",
            "2 -,Pot number missing from sample info.",
            "- -,Pot number missing from sample info.",
    })
    void testCheckPotNumbers(String potNumberArgs, String expectedProblem) {
        String[] potNumbersStrings = potNumberArgs.split("\\s+");
        Integer[] potNumbers = Arrays.stream(potNumbersStrings)
                .map(s -> s.equals("-") ? null : Integer.valueOf(s))
                .toArray(Integer[]::new);
        List<OriginalSampleData> osds = Arrays.stream(potNumbers).map(p -> {
            OriginalSampleData os = new OriginalSampleData();
            os.setPotNumber(p);
            return os;
        }).toList();
        List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        service.checkPotNumbers(problems, new OriginalSampleRegisterRequest(osds));
        assertProblem(problems, expectedProblem);
    }

    @Test
    void testCompilePotGroups() {
        List<DataStruct> datas = IntStream.range(0,3)
                .mapToObj(i -> new DataStruct(new OriginalSampleData()))
                .toList();
        datas.get(0).getOriginalSampleData().setPotNumber(1);
        datas.get(1).getOriginalSampleData().setPotNumber(1);
        datas.get(2).getOriginalSampleData().setPotNumber(2);
        List<List<DataStruct>> groups = service.compilePotGroups(datas);
        assertThat(groups).containsExactly(List.of(datas.get(0), datas.get(1)), List.of(datas.get(2)));
    }

    @ParameterizedTest
    @MethodSource("checkPotGroupsArgs")
    void testCheckPotGroups(List<List<DataStruct>> groups, List<String> expectedProblems) {
        List<String> problems = new ArrayList<>();
        service.checkPotGroups(problems, groups);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> checkPotGroupsArgs() {
        Solution[] sols = IntStream.of(1,2).mapToObj(i -> new Solution(i, "sol"+i)).toArray(Solution[]::new);
        LabwareType[] lts = {EntityFactory.getTubeType(), EntityFactory.makeLabwareType(1,1)};
        Work[] works = EntityFactory.makeWorks("SGP1", "SGP2");
        List<DataStruct> smallGroup = List.of(new DataStruct(new OriginalSampleData()));
        List<DataStruct> goodGroup = List.of(new DataStruct(new OriginalSampleData()), new DataStruct(new OriginalSampleData()));
        List<DataStruct> ltGroup = List.of(new DataStruct(new OriginalSampleData()), new DataStruct(new OriginalSampleData()));
        List<DataStruct> solGroup = List.of(new DataStruct(new OriginalSampleData()), new DataStruct(new OriginalSampleData()));
        List<DataStruct> workGroup = List.of(new DataStruct(new OriginalSampleData()), new DataStruct(new OriginalSampleData()));
        Zip.of(Stream.of(smallGroup, goodGroup, ltGroup, solGroup, workGroup),
                Stream.of(1, 2, 3, 4, 5)).forEach((group, potNumber) -> {
            for (DataStruct data : group) {
                data.solution = sols[0];
                data.labwareType = lts[0];
                data.work = works[0];
                data.getOriginalSampleData().setPotNumber(potNumber);
            }
        });
        ltGroup.get(1).labwareType = lts[1];
        solGroup.get(1).solution = sols[1];
        workGroup.get(1).work = works[1];
        String ltError = "Inconsistent labware type specified for pot 3.";
        String solError = "Inconsistent solution specified for pot 4.";
        String workError = "Inconsistent work specified for pot 5.";

        return Arrays.stream(new Object[][] {
                {List.of(smallGroup, goodGroup), List.of()},
                {List.of(smallGroup, ltGroup), List.of(ltError)},
                {List.of(smallGroup, solGroup), List.of(solError)},
                {List.of(smallGroup, workGroup), List.of(workError)},
                {List.of(smallGroup, goodGroup, ltGroup, solGroup, workGroup),
                        List.of(ltError, solError, workError)},
        }).map(Arguments::of);
    }

    @Test
    public void testLoadDonors_none() {
        List<DataStruct> datas = Stream.of(osdWithDonor(""), osdWithDonor(null))
                .map(DataStruct::new).collect(toList());
        service.loadDonors(datas);
        datas.forEach(d -> assertNull(d.donor));
        verifyNoInteractions(mockDonorRepo);
    }

    @Test
    public void testLoadDonors() {
        List<DataStruct> datas = Stream.of(null, "DONOR1", "DONOR2")
                .map(dn -> new DataStruct(osdWithDonor(dn)))
                .collect(toList());
        Donor donor1 = new Donor(1, "DONOR1", null, null);
        Donor donor2 = new Donor(2, "DONOR2", null, null);
        when(mockDonorRepo.findAllByDonorNameIn(any())).thenReturn(List.of(donor1, donor2));

        service.loadDonors(datas);
        assertNull(datas.get(0).donor);
        assertSame(donor1, datas.get(1).donor);
        assertSame(donor2, datas.get(2).donor);
        verify(mockDonorRepo).findAllByDonorNameIn(Set.of("DONOR1", "DONOR2"));
    }

    @Test
    public void testCreateNewDonors() {
        Donor[] existingDonors = IntStream.range(1, 3)
                .mapToObj(i -> new Donor(i, "DONOR"+i, null, null))
                .toArray(Donor[]::new);
        Species human = new Species(1, Species.HUMAN_NAME);
        Species banana = new Species(2, "Banana");
        List<DataStruct> datas = List.of(
                dataStructOf("DONOR1", existingDonors[0], human, LifeStage.fetal),
                dataStructOf("DONOR2", existingDonors[1], banana, LifeStage.adult),
                dataStructOf("DONOR3", null, human, LifeStage.fetal),
                dataStructOf("DONOR4", null, banana, LifeStage.adult)
        );
        List<Donor> createdDonors = new ArrayList<>(2);
        when(mockDonorRepo.saveAll(any())).then(invocation -> {
            Iterable<Donor> unsavedDonors = invocation.getArgument(0);
            int newId = 3;
            for (Donor d : unsavedDonors) {
                assertNull(d.getId());
                d.setId(newId);
                ++newId;
                createdDonors.add(d);
            }
            return unsavedDonors;
        });

        service.createNewDonors(datas);
        assertThat(createdDonors).hasSize(2);
        Donor d3 = createdDonors.getFirst();
        assertEquals("DONOR3", d3.getDonorName());
        assertSame(human, d3.getSpecies());
        assertSame(LifeStage.fetal, d3.getLifeStage());
        Donor d4 = createdDonors.get(1);
        assertEquals("DONOR4", d4.getDonorName());
        assertSame(banana, d4.getSpecies());
        assertSame(LifeStage.adult, d4.getLifeStage());
        assertThat(datas.stream().map(d -> d.donor)).containsExactly(existingDonors[0], existingDonors[1], d3, d4);
    }

    static DataStruct dataStructOf(String donorName, Donor donor, Species species, LifeStage lifeStage) {
        DataStruct data = new DataStruct(osdWithDonor(donorName));
        data.donor = donor;
        data.species = species;
        data.getOriginalSampleData().setLifeStage(lifeStage);
        return data;
    }

    @Test
    public void testLinkBioRisks() {
        Operation[] ops = IntStream.range(100,102)
                .mapToObj(TestOriginalSampleRegisterService::opWithId).toArray(Operation[]::new);
        Sample[] samples = EntityFactory.makeSamples(5);
        BioRisk risk1 = new BioRisk(201, "risk1");
        BioRisk risk2 = new BioRisk(202, "risk2");
        List<List<DataStruct>> groups = List.of(
                List.of(dataForRisk(ops[0], samples[0], risk1),
                        dataForRisk(ops[0], samples[1], risk2),
                        dataForRisk(ops[0], samples[2], risk2)),
                List.of(dataForRisk(ops[1], samples[2], risk1))
        );
        service.linkBioRisks(groups);
        verify(mockBioRiskService, times(ops.length)).recordSampleBioRisks(any(), any());
        verify(mockBioRiskService).recordSampleBioRisks(
                Map.of(samples[0].getId(), risk1, samples[1].getId(), risk2, samples[2].getId(), risk2), ops[0].getId());
        verify(mockBioRiskService).recordSampleBioRisks(Map.of(samples[2].getId(), risk1), ops[1].getId());
    }

    private static DataStruct dataForRisk(Operation op, Sample sample, BioRisk risk) {
        DataStruct data = new DataStruct(null);
        data.operation = op;
        data.sample = sample;
        data.bioRisk = risk;
        return data;
    }

    private static Operation opWithId(Integer id) {
        Operation op = new Operation();
        op.setId(id);
        return op;
    }

    @Test
    public void testCreateNewSamples() {
        Species human = EntityFactory.getHuman();
        Species banana = new Species(human.getId()+1, "Banana");
        Donor[] donors = IntStream.range(0, 2)
                .mapToObj(i -> new Donor(i+1, "DONOR"+i, LifeStage.adult, human))
                .toArray(Donor[]::new);
        TissueType tt = EntityFactory.getTissueType();
        SpatialLocation sl0 = new SpatialLocation(1, "SL0", 0, tt);
        SpatialLocation sl1 = new SpatialLocation(2, "SL1", 1, tt);
        Fixative fix = EntityFactory.getFixative();
        Medium medium = new Medium(100, "None");
        BioState bs = new BioState(101, "Original tissue");
        BioState tisBs = new BioState(102, "Tissue");
        Hmdmc hmdmc = EntityFactory.getHmdmc();
        LocalDate date1 = LocalDate.of(2022, 10, 25);
        LabwareType cassetteLt = EntityFactory.makeLabwareType(1, 1, "Cassette");
        LabwareType provLt = EntityFactory.makeLabwareType(1, 1, "Proviasette");
        List<DataStruct> datas = List.of(
                dataOf(donors[0], sl0, "R1", "EXT1", fix, hmdmc, date1, human),
                dataOf(donors[1], sl1, null, null, fix,null, null, banana)
        );
        datas.get(0).setLabwareType(provLt);
        datas.get(1).setLabwareType(cassetteLt);
        when(mockMediumRepo.getByName("None")).thenReturn(medium);
        when(mockBsRepo.getByName("Original sample")).thenReturn(bs);
        when(mockBsRepo.getByName("Tissue")).thenReturn(tisBs);
        final List<Tissue> savedTissues = new ArrayList<>(2);
        when(mockTissueRepo.save(any())).then(invocation -> {
            Tissue unsaved = invocation.getArgument(0);
            assertNull(unsaved.getId());
            unsaved.setId(10 + savedTissues.size());
            savedTissues.add(unsaved);
            return unsaved;
        });

        final List<Sample> savedSamples = new ArrayList<>(2);
        when(mockSampleRepo.save(any())).then(invocation -> {
            Sample unsaved = invocation.getArgument(0);
            assertNull(unsaved.getId());
            unsaved.setId(20 + savedSamples.size());
            savedSamples.add(unsaved);
            return unsaved;
        });

        service.createNewSamples(datas);
        assertThat(savedSamples).hasSameSizeAs(datas);
        assertThat(savedTissues).hasSameSizeAs(datas);
        for (int i = 0; i < datas.size(); ++i) {
            final DataStruct data = datas.get(i);
            final OriginalSampleData osd = data.getOriginalSampleData();
            Sample sample = data.sample;
            Tissue tissue = sample.getTissue();
            assertSame(savedSamples.get(i), sample);
            assertSame(savedTissues.get(i), tissue);
            assertNotNull(sample.getId());
            assertNotNull(tissue.getId());
            assertNull(sample.getSection());
            assertSame(i==0 ? bs : tisBs, sample.getBioState());
            assertSame(osd.getReplicateNumber(), tissue.getReplicate());
            assertSame(osd.getExternalIdentifier(), tissue.getExternalName());
            assertSame(medium, tissue.getMedium());
            assertSame(fix, tissue.getFixative());
            assertSame(i==0 ? hmdmc : null, tissue.getHmdmc());
            assertSame(i==0 ? sl0 : sl1, tissue.getSpatialLocation());
            assertNull(tissue.getParentId());
            assertSame(i==0 ? date1 : null, tissue.getCollectionDate());
            assertSame(donors[i], tissue.getDonor());
        }
    }

    static DataStruct dataOf(Donor donor, SpatialLocation sl, String replicate, String extName, Fixative fix,
                             Hmdmc hmdmc, LocalDate date, Species species) {
        final OriginalSampleData osd = new OriginalSampleData();
        osd.setExternalIdentifier(extName);
        osd.setSampleCollectionDate(date);
        osd.setReplicateNumber(replicate);
        DataStruct ds = new DataStruct(osd);
        ds.donor = donor;
        ds.spatialLocation = sl;
        ds.fixative = fix;
        ds.hmdmc = hmdmc;
        ds.species = species;
        return ds;
    }

    @Test
    public void testLinkWorkOps() {
        Work[] works = IntStream.range(1, 3)
                .mapToObj(i -> EntityFactory.makeWork("SGP"+i))
                .toArray(Work[]::new);
        Operation[] ops = IntStream.range(1,5)
                .mapToObj(TestOriginalSampleRegisterService::opWithId)
                .toArray(Operation[]::new);
        Work[] opWorks = {null, works[0], works[1], works[0], null};
        List<DataStruct> datas = IntStream.range(0, ops.length)
                .mapToObj(i -> {
                    DataStruct data = new DataStruct(null);
                    data.operation = ops[i];
                    data.work = opWorks[i];
                    return data;
                })
                .toList();
        List<List<DataStruct>> groups = datas.stream().map(List::of).collect(toCollection(ArrayList::new));
        // make one of the groups have more than one data
        groups.set(0, List.of(datas.getFirst(), new DataStruct(null)));

        service.linkWork(groups);

        ArgumentCaptor<Stream<WorkOp>> captor = streamCaptor();
        verify(mockWorkService).linkWorkOps(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(
                new WorkOp(works[0], ops[1]),
                new WorkOp(works[1], ops[2]),
                new WorkOp(works[0], ops[3])
        );
    }

    @Test
    public void testCreateNewLabware() {
        Sample[] samples = EntityFactory.makeSamples(3);
        DataStruct[] datas = IntStream.range(0,samples.length)
                .mapToObj(i -> new DataStruct(new OriginalSampleData()))
                .toArray(DataStruct[]::new);
        Zip.of(Arrays.stream(datas), Arrays.stream(samples)).forEach((data, sam) -> data.sample = sam);
        datas[0].labwareType = datas[1].labwareType = EntityFactory.getTubeType();
        datas[2].labwareType = EntityFactory.makeLabwareType(1,1);
        List<List<DataStruct>> groups = List.of(List.of(datas[0], datas[1]), List.of(datas[2]));
        Labware[] lws = Stream.of(datas[0].labwareType, datas[2].labwareType)
                .map(lt -> {
                    Labware lw = EntityFactory.makeEmptyLabware(lt);
                    when(mockLabwareService.create(lt)).thenReturn(lw);
                    return lw;
                })
                .toArray(Labware[]::new);
        service.createNewLabware(groups);
        verify(mockLabwareService, times(groups.size())).create(any(LabwareType.class));
        for (int i = 0; i < groups.size(); ++i) {
            List<DataStruct> group = groups.get(i);
            for (DataStruct data : group) {
                assertSame(lws[i], data.labware);
            }
            Slot slot = lws[i].getFirstSlot();
            if (i==0) {
                assertThat(slot.getSamples()).containsExactlyInAnyOrder(samples[0], samples[1]);
            } else {
                assertThat(slot.getSamples()).containsExactly(samples[2]);
            }
            verify(mockSlotRepo).save(slot);
        }
    }

    @Test
    public void testRecordRegistrations() {
        List<Operation> ops = IntStream.range(0,2).mapToObj(TestOriginalSampleRegisterService::opWithId).toList();
        when(mockOpService.createOperationInPlace(any(), any(), any(), any(), any())).thenReturn(ops.get(0), ops.get(1));
        OperationType opType = EntityFactory.makeOperationType("Register", null, OperationTypeFlag.IN_PLACE);
        when(mockOpTypeRepo.getByName(opType.getName())).thenReturn(opType);
        User user = EntityFactory.getUser();
        List<DataStruct> datas = IntStream.range(0,2)
                .mapToObj(i -> new DataStruct(new OriginalSampleData()))
                .toList();
        for (int i = 0; i < datas.size(); ++i) {
            DataStruct data = datas.get(i);
            data.sample = new Sample(10+i, null, EntityFactory.getTissue(), EntityFactory.getBioState());
            data.labware = EntityFactory.makeLabware(EntityFactory.getTubeType(), data.sample);
        }
        DataStruct extraSample = new DataStruct(new OriginalSampleData());
        extraSample.sample = new Sample(100, null, EntityFactory.getTissue(), EntityFactory.getBioState());
        extraSample.labware = datas.getFirst().labware;
        List<List<DataStruct>> groups = datas.stream().map(List::of).collect(toCollection(ArrayList::new));
        groups.set(0, List.of(datas.getFirst(), extraSample));
        service.recordRegistrations(user, groups);
        verify(mockOpService, times(groups.size())).createOperationInPlace(any(), any(), any(), any(), any());
        for (int i = 0; i < datas.size(); ++i) {
            DataStruct data = datas.get(i);
            assertSame(ops.get(i), data.operation);
            verify(mockOpService).createOperationInPlace(same(opType), same(user), same(data.labware), isNull(), isNull());
        }
    }

    @Test
    public void testRecordSolutions() {
        Solution[] solutions = { new Solution(1, "sol1"), null };
        Sample[] samples = IntStream.range(1, 4)
                .mapToObj(i -> new Sample(10+i, null, EntityFactory.getTissue(), EntityFactory.getBioState()))
                .toArray(Sample[]::new);
        Labware[] lws = Arrays.stream(new int[][] {
                {0,1}, {2}
        }).map(arr -> {
            Sample[] lwSamples = Arrays.stream(arr).mapToObj(i -> samples[i]).toArray(Sample[]::new);
            Labware lw = EntityFactory.makeLabware(EntityFactory.getTubeType(), lwSamples[0]);
            for (int i = 1; i < lwSamples.length; ++i) {
                lw.getFirstSlot().addSample(lwSamples[i]);
            }
            return lw;
        }).toArray(Labware[]::new);
        Operation[] ops = IntStream.range(1, 3)
                .mapToObj(i -> {
                    Operation op = new Operation();
                    op.setId(100+i);
                    Slot slot = lws[i-1].getFirstSlot();
                    List<Action> actions = slot.getSamples().stream()
                            .map(sam -> new Action(1000+sam.getId(), op.getId(), slot, slot, sam, sam))
                            .toList();
                    op.setActions(actions);
                    return op;
                })
                .toArray(Operation[]::new);
        List<DataStruct> group1 = List.of(
                dataStructOf(solutions[0], samples[0], lws[0], ops[0]),
                dataStructOf(solutions[0], samples[1], lws[0], ops[0])
        );
        List<DataStruct> group2 = List.of(
                dataStructOf(null, samples[2], lws[1], ops[1])
        );
        List<List<DataStruct>> groups = List.of(group1, group2);

        service.recordSolutions(groups);
        verify(mockOpSolRepo).saveAll(Set.of(
                new OperationSolution(ops[0].getId(), solutions[0].getId(), lws[0].getId(), samples[0].getId()),
                new OperationSolution(ops[0].getId(), solutions[0].getId(), lws[0].getId(), samples[1].getId())
        ));
    }

    static DataStruct dataStructOf(Solution sol, Sample sam, Labware lw, Operation op) {
        DataStruct ds = new DataStruct(new OriginalSampleData());
        ds.solution = sol;
        ds.sample = sam;
        ds.labware = lw;
        ds.operation = op;
        return ds;
    }

    @Test
    public void testMakeResult() {
        Solution sol = new Solution(1, "Sol");
        Solution[] sols = {sol, sol, null};
        Labware[] lws = IntStream.range(0, sols.length)
                .mapToObj(i -> EntityFactory.makeLabware(EntityFactory.getTubeType()))
                .toArray(Labware[]::new);
        List<DataStruct> datas = IntStream.range(0, sols.length)
                .mapToObj(i -> {
                    DataStruct d = new DataStruct(new OriginalSampleData());
                    d.solution = sols[i];
                    d.labware = lws[i];
                    return d;
                }).toList();
        List<List<DataStruct>> groups = datas.stream().map(List::of).collect(toCollection(ArrayList::new));
        groups.set(0, List.of(datas.getFirst(), new DataStruct(null)));
        RegisterResult result = service.makeResult(groups);
        assertThat(result.getClashes()).isEmpty();
        assertThat(result.getLabware()).containsExactly(lws);
        assertThat(result.getLabwareSolutions()).containsExactlyInAnyOrder(
                new LabwareSolutionName(lws[0].getBarcode(), sol.getName()),
                new LabwareSolutionName(lws[1].getBarcode(), sol.getName())
        );
    }

    static OriginalSampleData osd(LifeStage lifeStage, String species, LocalDate date) {
        OriginalSampleData data = new OriginalSampleData();
        data.setLifeStage(lifeStage);
        data.setSpecies(species);
        data.setSampleCollectionDate(date);
        return data;
    }

    static OriginalSampleData osdWithDonor(String donorName) {
        OriginalSampleData data = new OriginalSampleData();
        data.setDonorIdentifier(donorName);
        return data;
    }

    static OriginalSampleData osdWithWorkNumber(String workNumber) {
        OriginalSampleData data = new OriginalSampleData();
        data.setWorkNumber(workNumber);
        return data;
    }

    static OriginalSampleData osdWithDonor(String donorName, LifeStage lifeStage, String species) {
        OriginalSampleData data = new OriginalSampleData();
        data.setDonorIdentifier(donorName);
        data.setLifeStage(lifeStage);
        data.setSpecies(species);
        return data;
    }

    static OriginalSampleData osdWithHmdmc(String hmdmc) {
        OriginalSampleData data = new OriginalSampleData();
        data.setHmdmc(hmdmc);
        return data;
    }

    static OriginalSampleData osdWithSL(String tissueTypeName, Integer slCode) {
        OriginalSampleData data = new OriginalSampleData();
        data.setTissueType(tissueTypeName);
        data.setSpatialLocation(slCode);
        return data;
    }

    static <G, S extends G> Stream<S> typeFilter(Stream<G> stream, Class<S> subtype) {
        return stream.filter(subtype::isInstance).map(subtype::cast);
    }
    static <G, S extends G> List<S> typeFilterToList(G[] items, Class<S> subtype) {
        return typeFilter(Arrays.stream(items), subtype).collect(toList());
    }
}