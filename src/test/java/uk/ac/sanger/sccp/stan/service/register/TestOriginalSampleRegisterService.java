package uk.ac.sanger.sccp.stan.service.register;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.register.*;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.register.OriginalSampleRegisterServiceImp.DataStruct;
import uk.ac.sanger.sccp.utils.UCMap;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.assertValidationException;

/**
 * Tests {@link OriginalSampleRegisterServiceImp}
 */
@SuppressWarnings("FieldCanBeLocal")
public class TestOriginalSampleRegisterService {
    private DonorRepo mockDonorRepo;
    private TissueRepo mockTissueRepo;
    private TissueTypeRepo mockTissueTypeRepo;
    private SampleRepo mockSampleRepo;
    private BioStateRepo mockBsRepo;
    private SlotRepo mockSlotRepo;
    private HmdmcRepo mockHmdmcRepo;
    private SpeciesRepo mockSpeciesRepo;
    private FixativeRepo mockFixativeRepo;
    private MediumRepo mockMediumRepo;
    private SolutionRepo mockSolutionRepo;
    private LabwareTypeRepo mockLtRepo;
    private OperationTypeRepo mockOpTypeRepo;
    private OperationSolutionRepo mockOpSolRepo;
    private Validator<String> mockDonorNameValidator;
    private Validator<String> mockExternalNameValidator;
    private Validator<String> mockHmdmcValidator;
    private Validator<String> mockReplicateValidator;
    private LabwareService mockLabwareService;
    private OperationService mockOpService;

    OriginalSampleRegisterServiceImp service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setup() {
        mockDonorRepo = mock(DonorRepo.class);
        mockTissueRepo = mock(TissueRepo.class);
        mockTissueTypeRepo = mock(TissueTypeRepo.class);
        mockSampleRepo = mock(SampleRepo.class);
        mockBsRepo = mock(BioStateRepo.class);
        mockSlotRepo = mock(SlotRepo.class);
        mockHmdmcRepo = mock(HmdmcRepo.class);
        mockSpeciesRepo = mock(SpeciesRepo.class);
        mockFixativeRepo = mock(FixativeRepo.class);
        mockMediumRepo = mock(MediumRepo.class);
        mockSolutionRepo = mock(SolutionRepo.class);
        mockLtRepo = mock(LabwareTypeRepo.class);
        mockOpTypeRepo = mock(OperationTypeRepo.class);
        mockOpSolRepo = mock(OperationSolutionRepo.class);
        mockDonorNameValidator = mock(Validator.class);
        mockExternalNameValidator = mock(Validator.class);
        mockHmdmcValidator = mock(Validator.class);
        mockReplicateValidator = mock(Validator.class);
        mockLabwareService = mock(LabwareService.class);
        mockOpService = mock(OperationService.class);

        service = spy(new OriginalSampleRegisterServiceImp(mockDonorRepo, mockTissueRepo, mockTissueTypeRepo,
                mockSampleRepo, mockBsRepo, mockSlotRepo, mockHmdmcRepo, mockSpeciesRepo, mockFixativeRepo,
                mockMediumRepo, mockSolutionRepo, mockLtRepo, mockOpTypeRepo, mockOpSolRepo, mockDonorNameValidator, mockExternalNameValidator,
                mockHmdmcValidator, mockReplicateValidator, mockLabwareService, mockOpService));
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
                "R1", "EXT1", "LT1", "SOL1", "FIX1", "SPEC1", LocalDate.of(2022,1,1));
        OriginalSampleRegisterRequest request = new OriginalSampleRegisterRequest(List.of(data));

        doAnswer(invocation -> {
            Collection<String> problems = invocation.getArgument(0);
            OriginalSampleRegisterRequest req = invocation.getArgument(1);
            String fieldName = invocation.getArgument(2);
            Function<OriginalSampleData, ?> func = invocation.getArgument(3);

            problems.add(fieldName+" wrong: "+func.apply(req.getSamples().get(0)));
            return null;
        }).when(service).checkFormat(any(), any(), any(), any(), anyBoolean(), any());

        doNothing().when(service).checkHmdmcsForSpecies(any(), any());
        doNothing().when(service).checkCollectionDates(any(), any());
        doNothing().when(service).checkExistence(any(), any(), any(), any(), any(), any());

        doNothing().when(service).loadDonors(any());
        doNothing().when(service).checkTissueTypesAndSpatialLocations(any(), any());

        doNothing().when(service).checkExternalNamesUnique(any(), any());
        doNothing().when(service).checkDonorFieldsAreConsistent(any(), any());

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

        verifyValidationMethods(request);

        verify(service, never()).createNewDonors(any());
        verify(service, never()).createNewSamples(any());
        verify(service, never()).createNewLabware(any());
        verify(service, never()).recordRegistrations(any(), any());
        verify(service, never()).recordSolutions(any());
        verify(service, never()).makeResult(any());
    }

    @Test
    public void testRegister_valid() {
        User user = EntityFactory.getUser();
        OriginalSampleData data = new OriginalSampleData("DONOR1", LifeStage.adult, "HMDMC1", "TISSUE1", 5,
                "R1", "EXT1", "LT1", "SOL1", "FIX1", "SPEC1", LocalDate.of(2022,1,1));
        OriginalSampleRegisterRequest request = new OriginalSampleRegisterRequest(List.of(data));
        doNothing().when(service).checkFormat(any(), any(), any(), any(), anyBoolean(), any());
        doNothing().when(service).checkHmdmcsForSpecies(any(), any());
        doNothing().when(service).checkCollectionDates(any(), any());

        doNothing().when(service).checkExistence(any(), any(), any(), any(), any(), any());

        doNothing().when(service).loadDonors(any());
        doNothing().when(service).checkExternalNamesUnique(any(), any());
        doNothing().when(service).checkDonorFieldsAreConsistent(any(), any());
        doNothing().when(service).checkTissueTypesAndSpatialLocations(any(), any());

        doNothing().when(service).createNewDonors(any());
        doNothing().when(service).createNewSamples(any());
        doNothing().when(service).createNewLabware(any());
        doNothing().when(service).recordRegistrations(any(), any());
        doNothing().when(service).recordSolutions(any());
        final RegisterResult expectedResult = new RegisterResult(List.of(EntityFactory.getTube()));
        doReturn(expectedResult).when(service).makeResult(any());

        assertSame(expectedResult, service.register(user, request));
        var datas = verifyValidationMethods(request);

        verify(service).createNewDonors(same(datas));
        verify(service).createNewSamples(same(datas));
        verify(service).createNewLabware(same(datas));
        verify(service).recordRegistrations(same(user), same(datas));
        verify(service).recordSolutions(same(datas));
        verify(service).makeResult(same(datas));
    }

    @SuppressWarnings("unchecked")
    private List<DataStruct> verifyValidationMethods(OriginalSampleRegisterRequest request) {
        ArgumentCaptor<Collection<String>> problemsArgCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(service).checkFormat(problemsArgCaptor.capture(), same(request), eq("Donor identifier"), any(), eq(true), same(mockDonorNameValidator));
        Collection<String> problems = problemsArgCaptor.getValue();
        verify(service).checkFormat(same(problems), same(request), eq("External identifier"), any(), eq(false), same(mockExternalNameValidator));
        verify(service).checkFormat(same(problems), same(request), eq("Life stage"), any(), eq(true), isNull());
        verify(service).checkFormat(same(problems), same(request), eq("HuMFre number"), any(), eq(false), same(mockHmdmcValidator));
        verify(service).checkFormat(same(problems), same(request), eq("Replicate number"), any(), eq(false), same(mockReplicateValidator));
        verify(service).checkFormat(same(problems), same(request), eq("Species"), any(), eq(true), isNull());
        verify(service).checkFormat(same(problems), same(request), eq("Tissue type"), any(), eq(true), isNull());
        verify(service).checkFormat(same(problems), same(request), eq("Spatial location"), any(), eq(true), isNull());
        verify(service).checkFormat(same(problems), same(request), eq("Fixative"), any(), eq(true), isNull());
        verify(service).checkFormat(same(problems), same(request), eq("Solution"), any(), eq(true), isNull());
        verify(service).checkFormat(same(problems), same(request), eq("Labware type"), any(), eq(true), isNull());

        ArgumentCaptor<List<DataStruct>> dataStructArgCaptor = ArgumentCaptor.forClass(List.class);

        verify(service).checkHmdmcsForSpecies(same(problems), same(request));
        verify(service).checkCollectionDates(same(problems), same(request));
        verify(service).checkExistence(same(problems), dataStructArgCaptor.capture(), eq("HuMFre number"), any(), any(), any());
        List<DataStruct> datas = dataStructArgCaptor.getValue();
        verify(service).checkExistence(same(problems), same(datas), eq("species"), any(), any(), any());
        verify(service).checkExistence(same(problems), same(datas), eq("fixative"), any(), any(), any());
        verify(service).checkExistence(same(problems), same(datas), eq("solution"), any(), any(), any());
        verify(service).checkExistence(same(problems), same(datas), eq("labware type"), any(), any(), any());

        verify(service).loadDonors(same(datas));
        verify(service).checkDonorFieldsAreConsistent(same(problems), same(datas));

        verify(service).checkTissueTypesAndSpatialLocations(same(problems), same(datas));
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
        LocalDate yesterday = LocalDate.now().minus(1, ChronoUnit.DAYS);
        LocalDate tomorrow = LocalDate.now().plus(1, ChronoUnit.DAYS);
        return Arrays.stream(new Object[][] {
                {
                        osd(LifeStage.fetal, "Human", yesterday),
                        osd(LifeStage.adult, "Human", yesterday),
                        osd(LifeStage.paediatric, "Hamster", null),
                        osd(LifeStage.adult, "Human", null),
                },
                {
                    osd(LifeStage.adult, "Hamster", tomorrow),
                        "Collection date must be in the past.",
                },
                {
                    osd(LifeStage.fetal, "Human", null),
                        "Collection date is required for fetal samples.",
                },
                {
                    osd(LifeStage.fetal, "Human", null),
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
    public void testCheckDonorFieldsAreConsistent(List<DataStruct> datas,
                                                  Collection<String> expectedProblems) {
        List<String> problems = new ArrayList<>(expectedProblems.size());
        service.checkDonorFieldsAreConsistent(problems, datas);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> checkDonorFieldsArgs() {
        Species human = new Species(1, "Human");
        Species banana = new Species(2, "Banana");
        Donor donor1 = new Donor(1, "DONOR1", LifeStage.adult, human);
        Donor donor2 = new Donor(2, "DONOR2", LifeStage.fetal, banana);
        UCMap<Donor> donors = UCMap.from(Donor::getDonorName, donor1, donor2);
        OriginalSampleData correct1 = osdWithDonor(donor1.getDonorName(), LifeStage.adult, "human");
        OriginalSampleData correct2 = osdWithDonor(donor2.getDonorName(), LifeStage.fetal, "banana");
        OriginalSampleData new1A = osdWithDonor("DONOR3", LifeStage.adult, "Human");
        OriginalSampleData new1B = osdWithDonor("DONOR3", LifeStage.fetal, "Human");
        OriginalSampleData new2A = osdWithDonor("DONOR4", LifeStage.adult, "Human");
        OriginalSampleData new2B = osdWithDonor("DONOR4", LifeStage.adult, "Banana");
        OriginalSampleData missing1 = osdWithDonor("DONOR1", null, "Human");
        OriginalSampleData missing2 = osdWithDonor("DONOR2", LifeStage.fetal, null);
        OriginalSampleData missing3 = osdWithDonor(null, LifeStage.fetal, "Human");
        OriginalSampleData wrong1 = osdWithDonor("donor1", LifeStage.paediatric, "human");
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
    @MethodSource("checkHmdmcsForSpeciesArgs")
    public void testCheckHmdmcsForSpecies(OriginalSampleRegisterRequest request, Collection<String> expectedProblems) {
        List<String> problems = new ArrayList<>(expectedProblems.size());
        service.checkHmdmcsForSpecies(problems, request);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> checkHmdmcsForSpeciesArgs() {
        OriginalSampleData humanWithHmdmc = osdWithHmdmc("HMDMC1", "Human");
        OriginalSampleData bananaWithoutHmdmc = osdWithHmdmc(null, "Banana");
        OriginalSampleData humanWithoutHmdmc = osdWithHmdmc(null, "Human");
        OriginalSampleData humanWithEmptyHmdmc = osdWithHmdmc("", "Human");
        OriginalSampleData bananaWithHmdmc = osdWithHmdmc("HMDMC1", "Banana");
        OriginalSampleData nullWithNull = osdWithHmdmc(null, null);
        OriginalSampleData nullWithHmdmc = osdWithHmdmc("HMDMC1", null);
        final String HMDMC_MISSING = "HuMFre number missing for human samples.";
        final String HMDMC_UNEXPECTED = "HuMFre number not expected for non-human samples.";

        return Arrays.stream(new Object[][] {
                { humanWithHmdmc, bananaWithoutHmdmc, nullWithHmdmc, nullWithNull },
                { humanWithoutHmdmc, HMDMC_MISSING },
                { humanWithEmptyHmdmc, HMDMC_MISSING },
                { bananaWithHmdmc, HMDMC_UNEXPECTED },
                { humanWithHmdmc, bananaWithoutHmdmc, humanWithoutHmdmc, bananaWithHmdmc, HMDMC_MISSING, HMDMC_UNEXPECTED },
        }).map(arr -> Arguments.of(new OriginalSampleRegisterRequest(typeFilterToList(arr, OriginalSampleData.class)),
                typeFilterToList(arr, String.class)));
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
        Tissue tissue = new Tissue(1, "EXT1", null, null, null, null, null, null, null, null);
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
    public void testCheckTissueTypesAndSpatialLocations(List<DataStruct> datas,
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
        for (TissueType tt : List.of(arm, leg)) {
            List<SpatialLocation> sls = List.of(
                    new SpatialLocation(10*tt.getId(), "Unknown", 0, tt),
                    new SpatialLocation(10*tt.getId()+1, "Alpha", 1, tt)
            );
            tt.setSpatialLocations(sls);
        }
        final SpatialLocation arm0 = arm.getSpatialLocations().get(0);
        final SpatialLocation arm1 = arm.getSpatialLocations().get(1);
        final SpatialLocation leg0 = leg.getSpatialLocations().get(0);

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
        Species human = new Species(1, "Human");
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
        Donor d3 = createdDonors.get(0);
        assertEquals(d3.getDonorName(), "DONOR3");
        assertSame(d3.getSpecies(), human);
        assertSame(d3.getLifeStage(), LifeStage.fetal);
        Donor d4 = createdDonors.get(1);
        assertEquals(d4.getDonorName(), "DONOR4");
        assertSame(d4.getSpecies(), banana);
        assertSame(d4.getLifeStage(), LifeStage.adult);
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
        Hmdmc hmdmc = EntityFactory.getHmdmc();
        LocalDate date1 = LocalDate.of(2022, 10, 25);
        List<DataStruct> datas = List.of(
                dataOf(donors[0], sl0, "R1", "EXT1", fix, hmdmc, date1, human),
                dataOf(donors[1], sl1, null, null, fix,null, null, banana)
        );
        when(mockMediumRepo.getByName("None")).thenReturn(medium);
        when(mockBsRepo.getByName("Original sample")).thenReturn(bs);
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
            assertSame(bs, sample.getBioState());
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
    public void testCreateNewLabware() {
        DataStruct[] datas = IntStream.range(0,2)
                .mapToObj(i -> new DataStruct(new OriginalSampleData()))
                .toArray(DataStruct[]::new);
        Sample sam = EntityFactory.getSample();
        datas[0].sample = sam;
        datas[1].sample = new Sample(sam.getId()+1, null, sam.getTissue(), sam.getBioState());
        datas[0].labwareType = EntityFactory.getTubeType();
        datas[1].labwareType = EntityFactory.makeLabwareType(1,1);

        Labware[] lws = Arrays.stream(datas)
                .map(d -> {
                    Labware lw = EntityFactory.makeEmptyLabware(d.labwareType);
                    when(mockLabwareService.create(d.labwareType)).thenReturn(lw);
                    return lw;
                })
                .toArray(Labware[]::new);
        service.createNewLabware(Arrays.asList(datas));
        verify(mockLabwareService, times(2)).create(any(LabwareType.class));
        for (int i = 0; i < datas.length; ++i) {
            assertSame(lws[i], datas[i].labware);
            Slot slot = lws[i].getFirstSlot();
            assertThat(slot.getSamples()).containsExactly(datas[i].sample);
            verify(mockSlotRepo).save(slot);
        }
    }

    @Test
    public void testRecordRegistrations() {
        List<Operation> ops = IntStream.range(0,2).mapToObj(i -> {
            Operation op = new Operation();
            op.setId(i);
            return op;
        }).collect(toList());
        when(mockOpService.createOperationInPlace(any(), any(), any(), any(), any())).thenReturn(ops.get(0), ops.get(1));
        OperationType opType = EntityFactory.makeOperationType("Register", null, OperationTypeFlag.IN_PLACE);
        when(mockOpTypeRepo.getByName(opType.getName())).thenReturn(opType);
        User user = EntityFactory.getUser();
        List<DataStruct> datas = IntStream.range(0,2)
                .mapToObj(i -> new DataStruct(new OriginalSampleData()))
                .collect(toList());
        for (int i = 0; i < datas.size(); ++i) {
            DataStruct data = datas.get(i);
            data.sample = new Sample(10+i, null, EntityFactory.getTissue(), EntityFactory.getBioState());
            data.labware = EntityFactory.makeLabware(EntityFactory.getTubeType(), data.sample);
        }
        service.recordRegistrations(user, datas);
        verify(mockOpService, times(datas.size())).createOperationInPlace(any(), any(), any(), any(), any());
        for (int i = 0; i < datas.size(); ++i) {
            DataStruct data = datas.get(i);
            assertSame(ops.get(i), data.operation);
            verify(mockOpService).createOperationInPlace(same(opType), same(user), same(data.labware), isNull(), isNull());
        }
    }

    @Test
    public void testRecordSolutions() {
        Solution[] solutions = { new Solution(1, "sol1"), null };
        Sample[] samples = IntStream.range(1, 3)
                .mapToObj(i -> new Sample(10+i, null, EntityFactory.getTissue(), EntityFactory.getBioState()))
                .toArray(Sample[]::new);
        Labware[] lws = Arrays.stream(samples)
                .map(sam -> EntityFactory.makeLabware(EntityFactory.getTubeType(), sam))
                .toArray(Labware[]::new);
        Operation[] ops = IntStream.range(1, 3)
                .mapToObj(i -> {
                    Operation op = new Operation();
                    op.setId(100+i);
                    Slot slot = lws[i-1].getFirstSlot();
                    Sample sample = samples[i-1];
                    op.setActions(List.of(new Action(1000+i, op.getId(), slot, slot, sample, sample)));
                    return op;
                })
                .toArray(Operation[]::new);
        List<DataStruct> datas = IntStream.range(0, solutions.length)
                .mapToObj(i -> {
                    DataStruct ds = new DataStruct(new OriginalSampleData());
                    ds.solution = solutions[i];
                    ds.sample = samples[i];
                    ds.labware = lws[i];
                    ds.operation = ops[i];
                    return ds;
                }).collect(toList());

        service.recordSolutions(datas);
        verify(mockOpSolRepo).saveAll(Set.of(
                new OperationSolution(ops[0].getId(), solutions[0].getId(), lws[0].getId(), samples[0].getId())
        ));
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
                }).collect(toList());
        RegisterResult result = service.makeResult(datas);
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

    static OriginalSampleData osdWithDonor(String donorName, LifeStage lifeStage, String species) {
        OriginalSampleData data = new OriginalSampleData();
        data.setDonorIdentifier(donorName);
        data.setLifeStage(lifeStage);
        data.setSpecies(species);
        return data;
    }

    static OriginalSampleData osdWithHmdmc(String hmdmc, String species) {
        OriginalSampleData data = new OriginalSampleData();
        data.setHmdmc(hmdmc);
        data.setSpecies(species);
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