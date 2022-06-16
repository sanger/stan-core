package uk.ac.sanger.sccp.stan.service.register;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.register.*;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.utils.UCMap;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
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
        doReturn(null).when(service).checkExistence(any(), any(), any(), any(), any());

        UCMap<Donor> donors = UCMap.from(Donor::getDonorName, new Donor(100, "DONOR1", LifeStage.adult, null));
        doReturn(donors).when(service).loadDonors(any());
        UCMap<TissueType> tissueTypes = UCMap.from(TissueType::getName, new TissueType(5, "TISSUE1", "TIS"));
        doReturn(tissueTypes).when(service).checkTissueTypesAndSpatialLocations(any(), any());

        doNothing().when(service).checkExternalNamesUnique(any(), any());
        doNothing().when(service).checkDonorFieldsAreConsistent(any(), any(), any());
        doNothing().when(service).checkDonorSpatialLocationUnique(any(), any(), any(), any());

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

        verifyValidationMethods(request, donors);

        verify(service, never()).createNewDonors(any(), any(), any());
        verify(service, never()).createNewTissues(any(), any(), any(), any(), any());
        verify(service, never()).createSamples(any(), any());
        verify(service, never()).createLabware(any(), any(), any());
        verify(service, never()).recordRegistrations(any(), any());
        verify(service, never()).recordSolutions(any(), any());
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

        UCMap<Hmdmc> hmdmcs = UCMap.from(Hmdmc::getHmdmc, new Hmdmc(10, "HMDMC1"));
        UCMap<Species> species = UCMap.from(Species::getName, new Species(20, "SPEC1"));
        UCMap<Fixative> fixatives = UCMap.from(Fixative::getName, new Fixative(30, "FIX1"));
        UCMap<Solution> solutions = UCMap.from(Solution::getName, new Solution(40, "SOL1", true));
        UCMap<LabwareType> lwTypes = UCMap.from(LabwareType::getName, new LabwareType(50, "LT1", 1, 1, null, false));

        UCMap<Donor> donors = UCMap.from(Donor::getDonorName, new Donor(100, "DONOR1", LifeStage.adult, null));
        UCMap<TissueType> ttypes = UCMap.from(TissueType::getName, EntityFactory.getTissueType());

        doReturn(hmdmcs).when(service).checkExistence(any(), any(), eq("HuMFre number"), any(), any());
        doReturn(species).when(service).checkExistence(any(), any(), eq("species"), any(), any());
        doReturn(fixatives).when(service).checkExistence(any(), any(), eq("fixative"), any(), any());
        doReturn(solutions).when(service).checkExistence(any(), any(), eq("solution"), any(), any());
        doReturn(lwTypes).when(service).checkExistence(any(), any(), eq("labware type"), any(), any());

        doReturn(donors).when(service).loadDonors(any());
        doNothing().when(service).checkExternalNamesUnique(any(), any());
        doNothing().when(service).checkDonorFieldsAreConsistent(any(), any(), any());
        doReturn(ttypes).when(service).checkTissueTypesAndSpatialLocations(any(), any());
        doNothing().when(service).checkDonorSpatialLocationUnique(any(), any(), any(), any());

        doNothing().when(service).createNewDonors(any(), any(), any());
        Map<OriginalSampleData, Tissue> tissues = Map.of(data, EntityFactory.getTissue());
        doReturn(tissues).when(service).createNewTissues(any(), any(), any(), any(), any());
        Map<OriginalSampleData, Sample> samples = Map.of(data, EntityFactory.getSample());
        doReturn(samples).when(service).createSamples(any(), any());
        Map<OriginalSampleData, Labware> labware = Map.of(data, EntityFactory.getTube());
        doReturn(labware).when(service).createLabware(any(), any(), any());
        Map<OriginalSampleData, Operation> ops = Map.of(data, new Operation());
        doReturn(ops).when(service).recordRegistrations(any(), any());
        doNothing().when(service).recordSolutions(any(), any());

        RegisterResult result = service.register(user, request);

        verifyValidationMethods(request, donors);

        verify(service).createNewDonors(request, donors, species);
        verify(service).createNewTissues(request, donors, ttypes, hmdmcs, fixatives);
        verify(service).createSamples(request, tissues);
        verify(service).createLabware(request, lwTypes, samples);
        verify(service).recordRegistrations(user, labware);
        verify(service).recordSolutions(ops, solutions);

        assertThat(result.getClashes()).isEmpty();
        assertThat(result.getLabware()).containsExactlyElementsOf(labware.values());
    }

    private void verifyValidationMethods(OriginalSampleRegisterRequest request, UCMap<Donor> donors) {
        verify(service).checkFormat(any(), same(request), eq("Donor identifier"), any(), eq(true), same(mockDonorNameValidator));
        verify(service).checkFormat(any(), same(request), eq("External identifier"), any(), eq(false), same(mockExternalNameValidator));
        verify(service).checkFormat(any(), same(request), eq("Life stage"), any(), eq(true), isNull());
        verify(service).checkFormat(any(), same(request), eq("HuMFre number"), any(), eq(false), same(mockHmdmcValidator));
        verify(service).checkFormat(any(), same(request), eq("Replicate number"), any(), eq(false), same(mockReplicateValidator));
        verify(service).checkFormat(any(), same(request), eq("Species"), any(), eq(true), isNull());
        verify(service).checkFormat(any(), same(request), eq("Tissue type"), any(), eq(true), isNull());
        verify(service).checkFormat(any(), same(request), eq("Spatial location"), any(), eq(true), isNull());
        verify(service).checkFormat(any(), same(request), eq("Fixative"), any(), eq(true), isNull());
        verify(service).checkFormat(any(), same(request), eq("Solution"), any(), eq(true), isNull());
        verify(service).checkFormat(any(), same(request), eq("Labware type"), any(), eq(true), isNull());

        verify(service).checkHmdmcsForSpecies(any(), same(request));
        verify(service).checkCollectionDates(any(), same(request));
        verify(service).checkExistence(any(), same(request), eq("HuMFre number"), any(), any());
        verify(service).checkExistence(any(), same(request), eq("species"), any(), any());
        verify(service).checkExistence(any(), same(request), eq("fixative"), any(), any());
        verify(service).checkExistence(any(), same(request), eq("solution"), any(), any());
        verify(service).checkExistence(any(), same(request), eq("labware type"), any(), any());

        verify(service).loadDonors(same(request));
        verify(service).checkExternalNamesUnique(any(), same(request));
        verify(service).checkDonorFieldsAreConsistent(any(), same(request), same(donors));

        verify(service).checkTissueTypesAndSpatialLocations(any(), same(request));
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
        OriginalSampleRegisterRequest request;
        OriginalSampleData data1 = osdWithDonor("DONOR1");
        OriginalSampleData data2 = osdWithDonor("DONOR2");
        OriginalSampleData data3 = osdWithDonor(null);
        if (anyUnknown) {
            OriginalSampleData data4 = osdWithDonor("DONORX");
            request = new OriginalSampleRegisterRequest(List.of(data1, data2, data3, data4));
        } else {
            request = new OriginalSampleRegisterRequest(List.of(data1, data2, data3));
        }
        Function<OriginalSampleData, String> fieldFunc = OriginalSampleData::getDonorIdentifier;
        List<Donor> donors = List.of(new Donor(1, "DONOR1", null, null), new Donor(2, "DONOR2", null, null));
        Function<String, Optional<Donor>> repoFunc = string -> donors.stream()
                .filter(d -> d.getDonorName().equalsIgnoreCase(string))
                .findAny();
        List<String> expectedProblems;
        if (anyUnknown) {
            expectedProblems = List.of("Unknown FIELD: [DONORX]");
        } else {
            expectedProblems = List.of();
        }
        List<String> problems = new ArrayList<>(expectedProblems.size());
        service.checkExistence(problems, request, "FIELD", fieldFunc, repoFunc);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
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
    public void testCheckDonorFieldsAreConsistent(OriginalSampleRegisterRequest request,
                                                  UCMap<Donor> donors,
                                                  Collection<String> expectedProblems) {
        List<String> problems = new ArrayList<>(expectedProblems.size());
        service.checkDonorFieldsAreConsistent(problems, request, donors);
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
        }).map(arr -> Arguments.of(new OriginalSampleRegisterRequest(typeFilterToList(arr, OriginalSampleData.class)),
                donors, typeFilterToList(arr, String.class)));
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
    @MethodSource("checkDonorSpatialArgs")
    public void testCheckDonorSpatialLocationUnique(OriginalSampleRegisterRequest request, UCMap<Donor> donors,
                                                    UCMap<TissueType> tissueTypes, Collection<Tissue> existingTissues,
                                                    Collection<String> expectedProblems) {
        if (existingTissues==null || existingTissues.isEmpty()) {
            when(mockTissueRepo.findAllByDonorIdAndSpatialLocationId(anyInt(), anyInt())).thenReturn(List.of());
        } else {
            when(mockTissueRepo.findAllByDonorIdAndSpatialLocationId(anyInt(), anyInt())).then(invocation -> {
                final int donorId = invocation.getArgument(0);
                final int slId = invocation.getArgument(1);
                return existingTissues.stream()
                        .filter(t -> t.getDonor().getId() == donorId && t.getSpatialLocation().getId() == slId)
                        .collect(toList());
            });
        }

        List<String> problems = new ArrayList<>(expectedProblems.size());
        service.checkDonorSpatialLocationUnique(problems, request, donors, tissueTypes);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> checkDonorSpatialArgs() {
        Donor d1 = new Donor(1, "DONOR1", LifeStage.adult, null);
        Donor d2 = new Donor(2, "DONOR2", LifeStage.fetal, null);
        UCMap<Donor> donors = UCMap.from(Donor::getDonorName, d1, d2);
        TissueType t1 = new TissueType(1, "Arm", "ARM");
        TissueType t2 = new TissueType(2, "Leg", "LEG");
        UCMap<TissueType> tissueTypes = UCMap.from(TissueType::getName, t1, t2);
        for (TissueType tt : tissueTypes.values()) {
            List<SpatialLocation> sls = List.of(
                    new SpatialLocation(10*tt.getId(), "Unknown", 0, tt),
                    new SpatialLocation(10*tt.getId()+1, "Alpha", 1, tt)
            );
            tt.setSpatialLocations(sls);
        }

        OriginalSampleData new1 = osdWithDonorSL("DONORX", "Arm", 0);
        OriginalSampleData new1b = osdWithDonorSL("donorX", "arm", 0);
        OriginalSampleData new2 = osdWithDonorSL("DONOR1", "ARM", 1);
        OriginalSampleData new2b = osdWithDonorSL("Donor1", "Arm", 1);
        OriginalSampleData new3 = osdWithDonorSL("DONOR1", "Leg", 0);
        OriginalSampleData new4 = osdWithDonorSL(null, "Arm", 0);
        OriginalSampleData new5 = osdWithDonorSL("DONOR1", null, 0);
        OriginalSampleData new6 = osdWithDonorSL("DONOR1", "Arm", null);
        OriginalSampleData old1 = osdWithDonorSL("donor1", "arm", 0);
        OriginalSampleData old2 = osdWithDonorSL("DONOR2", "LEG", 1);

        Tissue tissue1 = new Tissue(1, "EXT1", "R1", t1.getSpatialLocations().get(0), d1, null, null, null, null, null);
        Tissue tissue2 = new Tissue(2, "EXT2", "R2", t2.getSpatialLocations().get(1), d2, null, null, null, null, null);
        List<Tissue> existingTissues = List.of(tissue1, tissue2);

        return Arrays.stream(new Object[][] {
                { new1, new2, new3, new4, new5, new6 },
                { new1, new1b, new2, new2b,
                  "Same donor name, tissue type and spatial location specified multiple times: [(donorX, arm, 0), (Donor1, Arm, 1)]"},
                { new1, old1, old2,
                  "Tissue from donor DONOR1, Arm, spatial location 0 already exists in the database.",
                  "Tissue from donor DONOR2, Leg, spatial location 1 already exists in the database." },
                { new1, new1b, old1,
                        "Same donor name, tissue type and spatial location specified multiple times: [(donorX, arm, 0)]",
                        "Tissue from donor DONOR1, Arm, spatial location 0 already exists in the database." },
        }).map(arr -> {
            List<OriginalSampleData> osds = typeFilterToList(arr, OriginalSampleData.class);
            List<String> problems = typeFilterToList(arr, String.class);
            return Arguments.of(new OriginalSampleRegisterRequest(osds), donors, tissueTypes, existingTissues, problems);
        });
    }

    @ParameterizedTest
    @MethodSource("checkTissueTypesAndSpatialLocationsArgs")
    public void testCheckTissueTypesAndSpatialLocations(OriginalSampleRegisterRequest request,
                                                        List<TissueType> tissueTypes,
                                                        List<String> expectedProblems) {
        when(mockTissueTypeRepo.findAllByNameIn(any())).thenReturn(tissueTypes);
        List<String> problems = new ArrayList<>(expectedProblems.size());
        var result = service.checkTissueTypesAndSpatialLocations(problems, request);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
        assertThat(result.values()).containsExactlyInAnyOrderElementsOf(tissueTypes);
        Set<String> tissueNamesToLookUp = new HashSet<>();
        for (var data : request.getSamples()) {
            final String ttName = data.getTissueType();
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
        final TissueType tt1 = new TissueType(1, "Arm", "ARM");
        final TissueType tt2 = new TissueType(2, "Leg", "LEG");
        for (TissueType tt : List.of(tt1, tt2)) {
            List<SpatialLocation> sls = List.of(
                    new SpatialLocation(10*tt.getId(), "Unknown", 0, tt),
                    new SpatialLocation(10*tt.getId()+1, "Alpha", 1, tt)
            );
            tt.setSpatialLocations(sls);
        }
        return Arrays.stream(new Object[][] {
                { osdWithSL("Arm", 0), osdWithSL("Arm", 1), osdWithSL("LEG", 0),
                  tt1, tt2 },
                { osdWithSL("Arm", 0), osdWithSL("Flarm", 0), osdWithSL("Floop", null),
                  tt1, "Unknown tissue type: [\"Flarm\", \"Floop\"]" },
                { osdWithSL("ARM", 3), osdWithSL("Leg", -1), osdWithSL("ARM", 1),
                  tt1, tt2, "There is no spatial location 3 for tissue type Arm.", "There is no spatial location -1 for tissue type Leg." },
                { osdWithSL("arm", 0), osdWithSL("arm", 17), osdWithSL("Floop", null),
                  tt1, "There is no spatial location 17 for tissue type Arm.", "Unknown tissue type: [\"Floop\"]"},
        }).map(arr -> {
            OriginalSampleRegisterRequest request = new OriginalSampleRegisterRequest(typeFilterToList(arr, OriginalSampleData.class));
            List<TissueType> tissueTypes = typeFilterToList(arr, TissueType.class);
            List<String> expectedProblems = typeFilterToList(arr, String.class);
            return Arguments.of(request, tissueTypes, expectedProblems);
        });
    }

    @Test
    public void testLoadDonors_none() {
        OriginalSampleRegisterRequest request = new OriginalSampleRegisterRequest(List.of(
                osdWithDonor(""), osdWithDonor(null)
        ));
        assertThat(service.loadDonors(request)).isEmpty();
        verifyNoInteractions(mockDonorRepo);
    }

    @Test
    public void testLoadDonors() {
        OriginalSampleRegisterRequest request = new OriginalSampleRegisterRequest(List.of(
                osdWithDonor(null), osdWithDonor("DONOR1"), osdWithDonor("DONOR2")
        ));
        Donor donor1 = new Donor(1, "DONOR1", null, null);
        Donor donor2 = new Donor(2, "DONOR2", null, null);
        when(mockDonorRepo.findAllByDonorNameIn(any())).thenReturn(List.of(donor1, donor2));

        UCMap<Donor> donors = service.loadDonors(request);
        assertThat(donors).hasSize(2);
        assertSame(donors.get("DONOR1"), donor1);
        assertSame(donors.get("DONOR2"), donor2);
        verify(mockDonorRepo).findAllByDonorNameIn(Set.of("DONOR1", "DONOR2"));
    }

    @Test
    public void testCreateNewDonors() {
        Donor[] existingDonors = IntStream.range(1, 3)
                .mapToObj(i -> new Donor(i, "DONOR"+i, null, null))
                .toArray(Donor[]::new);
        Species human = new Species(1, "Human");
        Species banana = new Species(2, "Banana");
        UCMap<Species> speciesMap = UCMap.from(Species::getName, human, banana);
        UCMap<Donor> donorsMap = UCMap.from(Donor::getDonorName, existingDonors);
        OriginalSampleRegisterRequest request = new OriginalSampleRegisterRequest(List.of(
                osdWithDonor("DONOR3", LifeStage.fetal, "Human"),
                osdWithDonor("DONOR4", LifeStage.adult, "Banana")
        ));
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

        service.createNewDonors(request, donorsMap, speciesMap);
        assertThat(donorsMap).hasSize(4);
        assertThat(donorsMap.values()).contains(existingDonors);
        assertThat(donorsMap.values()).containsAll(createdDonors);
        assertThat(createdDonors).hasSize(2);
        Donor d = createdDonors.get(0);
        assertEquals(d.getDonorName(), "DONOR3");
        assertSame(d.getSpecies(), human);
        assertSame(d.getLifeStage(), LifeStage.fetal);
        d = createdDonors.get(1);
        assertEquals(d.getDonorName(), "DONOR4");
        assertSame(d.getSpecies(), banana);
        assertSame(d.getLifeStage(), LifeStage.adult);
    }

    @Test
    public void testCreateNewTissues() {
        Medium noneMedium = new Medium(100, "None");
        when(mockMediumRepo.getByName("None")).thenReturn(noneMedium);
        Donor donor = new Donor(1, "DONOR1", null, null);
        TissueType tt = new TissueType(2, "Arm", "ARM");
        SpatialLocation sl = new SpatialLocation(3, "Unknown", 0, tt);
        tt.setSpatialLocations(List.of(sl));
        Hmdmc hmdmc = new Hmdmc(4, "HMDMC1");
        Fixative fix = new Fixative(5, "FIX1");
        LocalDate date1 = LocalDate.of(2022, 1, 5);
        OriginalSampleRegisterRequest request = new OriginalSampleRegisterRequest(List.of(
                osd("donor1", "EXT1", "R1", "arm", 0, "fix1", "hmdmc1",
                        date1, "sol1"),
                osd("Donor1", "", "", "Arm", 0, "Fix1", "Hmdmc1",
                        null, "sol1")
        ));
        final List<Tissue> savedTissues = new ArrayList<>(2);
        when(mockTissueRepo.save(any())).then(invocation -> {
            Tissue unsaved = invocation.getArgument(0);
            assertNull(unsaved.getId());
            unsaved.setId(10 + savedTissues.size());
            savedTissues.add(unsaved);
            return unsaved;
        });

        var result = service.createNewTissues(
                request,
                UCMap.from(Donor::getDonorName, donor),
                UCMap.from(TissueType::getName, tt),
                UCMap.from(Hmdmc::getHmdmc, hmdmc),
                UCMap.from(Fixative::getName, fix)
        );
        assertThat(result).hasSize(2);
        assertThat(savedTissues).hasSize(2);
        assertThat(result.values()).containsExactlyInAnyOrderElementsOf(savedTissues);

        for (int i = 0; i < savedTissues.size(); i++) {
            Tissue tissue = savedTissues.get(i);
            if (i==0) {
                assertEquals("EXT1", tissue.getExternalName());
                assertEquals("R1", tissue.getReplicate());
                assertEquals(date1, tissue.getCollectionDate());
            } else {
                assertNull(tissue.getExternalName());
                assertNull(tissue.getReplicate());
                assertNull(tissue.getCollectionDate());
            }
            assertSame(donor, tissue.getDonor());
            assertSame(sl, tissue.getSpatialLocation());
            assertSame(noneMedium, tissue.getMedium());
            assertSame(hmdmc, tissue.getHmdmc());
            assertNull(tissue.getParentId());
        }
    }

    @Test
    public void testCreateSamples() {
        BioState bs = new BioState(1, "Original sample");
        when(mockBsRepo.getByName(bs.getName())).thenReturn(bs);
        OriginalSampleData data1 = osdWithDonor("DONOR1");
        OriginalSampleData data2 = osdWithDonor("DONOR2");
        Tissue[] tissues = IntStream.range(1,3)
                .mapToObj(i -> { Tissue t = new Tissue(); t.setId(i); return t; })
                .toArray(Tissue[]::new);
        Map<OriginalSampleData, Tissue> tissueMap = Map.of(data1, tissues[0], data2, tissues[1]);
        List<Sample> savedSamples = new ArrayList<>(2);
        when(mockSampleRepo.save(any())).then(invocation -> {
            Sample unsaved = invocation.getArgument(0);
            assertNull(unsaved.getId());
            unsaved.setId(10+savedSamples.size());
            savedSamples.add(unsaved);
            return unsaved;
        });

        var result = service.createSamples(
                new OriginalSampleRegisterRequest(List.of(data1, data2)), tissueMap
        );
        assertThat(result).hasSize(2);
        assertThat(savedSamples).hasSize(2);
        assertThat(result.values()).containsExactlyInAnyOrderElementsOf(savedSamples);

        for (int i = 0; i < savedSamples.size(); ++i) {
            Sample sample = savedSamples.get(i);
            assertSame(tissues[i], sample.getTissue());
            assertSame(bs, sample.getBioState());
            assertNull(sample.getSection());
        }
    }

    @Test
    public void testCreateLabware() {
        OriginalSampleData[] datas = { new OriginalSampleData(), new OriginalSampleData() };
        LabwareType[] lts = IntStream.range(0,2).mapToObj(i -> {
            LabwareType lt = EntityFactory.makeLabwareType(1,1);
            lt.setName("lt"+i);
            datas[i].setLabwareType(lt.getName());
            return lt;
        }).toArray(LabwareType[]::new);
        Labware[] lws = Arrays.stream(lts).map(EntityFactory::makeEmptyLabware).toArray(Labware[]::new);
        for (int i = 0; i < lts.length; ++i) {
            when(mockLabwareService.create(lts[i])).thenReturn(lws[i]);
        }

        Sample[] samples = new Sample[2];
        samples[0] = EntityFactory.getSample();
        samples[1] = new Sample(samples[0].getId(), 10, samples[0].getTissue(), samples[0].getBioState());
        UCMap<LabwareType> ltMap = UCMap.from(LabwareType::getName, lts);
        Map<OriginalSampleData, Sample> sampleMap = Map.of(datas[0], samples[0], datas[1], samples[1]);

        var result = service.createLabware(
                new OriginalSampleRegisterRequest(List.of(datas[0], datas[1])), ltMap, sampleMap
        );

        assertThat(result.values()).containsExactlyInAnyOrder(lws);
        for (int i = 0; i < lws.length; ++i) {
            verify(mockLabwareService).create(lts[i]);
            assertSame(lws[i], result.get(datas[i]));
            final Slot slot = lws[i].getFirstSlot();
            assertThat(slot.getSamples()).containsExactly(samples[i]);
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
        Sample sample = EntityFactory.getSample();
        LabwareType lt = EntityFactory.getTubeType();
        OriginalSampleData[] osds = IntStream.range(0,2)
                .mapToObj(i -> osdWithDonor(""+i))
                .toArray(OriginalSampleData[]::new);

        Labware[] labware = IntStream.range(0,2)
                .mapToObj(i -> EntityFactory.makeLabware(lt, sample))
                .toArray(Labware[]::new);

        Map<OriginalSampleData, Labware> lwMap = Map.of(osds[0], labware[0], osds[1], labware[1]);

        assertEquals(Map.of(osds[0], ops.get(0), osds[1], ops.get(1)), service.recordRegistrations(user, lwMap));

        assertThat(ops).hasSize(lwMap.size());
        for (Labware lw : labware) {
            verify(mockOpService).createOperationInPlace(opType, user, lw, null, null);
        }
    }

    @Test
    public void testRecordSolutions() {
        Solution[] solutions = { new Solution(1, "sol1"), new Solution(2, "sol2") };
        OriginalSampleData[] osds = Arrays.stream(solutions)
                .map(TestOriginalSampleRegisterService::osdForSolution)
                .toArray(OriginalSampleData[]::new);
        Operation[] ops = IntStream.range(0, 2)
                .mapToObj(i -> new Operation())
                .toArray(Operation[]::new);
        Sample sample = EntityFactory.getSample();
        LabwareType lt = EntityFactory.getTubeType();
        Labware[] lw = IntStream.range(0, 2)
                .mapToObj(i -> EntityFactory.makeLabware(lt, sample))
                .toArray(Labware[]::new);
        for (int i = 0; i < ops.length; ++i) {
            Operation op = ops[i];
            op.setId(10+i);
            Slot slot = lw[i].getFirstSlot();
            Action action = new Action(100+i, op.getId(), slot, slot, sample, sample);
            op.setActions(List.of(action));
        }
        Map<OriginalSampleData, Operation> opMap = Map.of(osds[0], ops[0], osds[1], ops[1]);
        UCMap<Solution> solutionMap = UCMap.from(Solution::getName, solutions);

        service.recordSolutions(opMap, solutionMap);

        Set<OperationSolution> expectedSolutions = new LinkedHashSet<>(2);
        for (int i = 0; i < ops.length; ++i) {
            expectedSolutions.add(new OperationSolution(ops[i].getId(), solutions[i].getId(), lw[i].getId(), sample.getId()));
        }
        verify(mockOpSolRepo).saveAll(expectedSolutions);
    }

    static OriginalSampleData osd(String donorName, String extName, String rep, String tissueTypeName, Integer slCode,
                                  String fixative, String hmdmc, LocalDate collectionDate, String solutionName) {
        OriginalSampleData data = new OriginalSampleData();
        data.setDonorIdentifier(donorName);
        data.setExternalIdentifier(extName);
        data.setReplicateNumber(rep);
        data.setTissueType(tissueTypeName);
        data.setSpatialLocation(slCode);
        data.setFixative(fixative);
        data.setHmdmc(hmdmc);
        data.setSampleCollectionDate(collectionDate);
        data.setSolution(solutionName);
        return data;
    }

    static OriginalSampleData osdForSolution(Solution solution) {
        OriginalSampleData data = new OriginalSampleData();
        if (solution!=null) {
            data.setSolution(solution.getName());
        }
        return data;
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

    static OriginalSampleData osdWithDonorSL(String donorName, String tissueTypeName, Integer slCode) {
        OriginalSampleData data = new OriginalSampleData();
        data.setDonorIdentifier(donorName);
        data.setTissueType(tissueTypeName);
        data.setSpatialLocation(slCode);
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