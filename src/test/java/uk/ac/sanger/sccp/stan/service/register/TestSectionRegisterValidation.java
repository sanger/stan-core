package uk.ac.sanger.sccp.stan.service.register;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.stubbing.Answer;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.register.*;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.sanitiser.DecimalSanitiser;
import uk.ac.sanger.sccp.stan.service.sanitiser.Sanitiser;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.EntityFactory.objToCollection;
import static uk.ac.sanger.sccp.stan.Matchers.assertProblem;
import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * Tests {@link SectionRegisterValidation}
 * @author dr6
 */
public class TestSectionRegisterValidation {
    @Mock private DonorRepo mockDonorRepo;
    @Mock private SpeciesRepo mockSpeciesRepo;
    @Mock private LabwareTypeRepo mockLwTypeRepo;
    @Mock private LabwareRepo mockLwRepo;
    @Mock private HmdmcRepo mockHmdmcRepo;
    @Mock private TissueTypeRepo mockTissueTypeRepo;
    @Mock private FixativeRepo mockFixativeRepo;
    @Mock private MediumRepo mockMediumRepo;
    @Mock private BioStateRepo mockBioStateRepo;
    @Mock private TissueRepo mockTissueRepo;
    @Mock private CellClassRepo mockCellClassRepo;
    @Mock private Validator<String> mockExternalBarcodeValidation;
    @Mock private Validator<String> mockDonorNameValidation;
    @Mock private Validator<String> mockExternalNameValidation;
    @Mock private Validator<String> mockReplicateValidator;
    @Mock private Validator<String> mockVisiumLpBarcodeValidation;
    @Mock private Validator<String> mockXeniumBarcodeValidator;
    private Sanitiser<String> thicknessSanisiser;
    @Mock private SlotRegionService mockSlotRegionService;
    @Mock private BioRiskService mockBioRiskService;
    @Mock private WorkService mockWorkService;
    
    private AutoCloseable mocking;

    @BeforeEach
    void setup() {
        thicknessSanisiser = new DecimalSanitiser("thickness", 1, BigDecimal.ZERO, null);
        mocking = MockitoAnnotations.openMocks(this);
    }
    
    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
    }

    private SectionRegisterValidation makeValidation(Object requestObj) {
        final String workNumber = "SGP1";
        SectionRegisterRequest request;
        if (requestObj instanceof SectionRegisterRequest) {
            request = (SectionRegisterRequest) requestObj;
        } else {
            Collection<?> col = objToCollection(requestObj);
            if (col.isEmpty()) {
                request = new SectionRegisterRequest(List.of(), workNumber);
            } else {
                Object element = col.iterator().next();
                if (element instanceof SectionRegisterLabware) {
                    request = new SectionRegisterRequest(objToCollection(requestObj), workNumber);
                } else if (element instanceof SectionRegisterContent) {
                    SectionRegisterLabware srl = new SectionRegisterLabware("X1", "lwtype", objToCollection(requestObj));
                    request = new SectionRegisterRequest(List.of(srl), workNumber);
                } else {
                    throw new IllegalArgumentException("Couldn't make "+requestObj+" into a request.");
                }
            }
        }
        return spy(new SectionRegisterValidation(request, mockDonorRepo, mockSpeciesRepo, mockLwTypeRepo, mockLwRepo,
                mockHmdmcRepo, mockTissueTypeRepo, mockFixativeRepo, mockCellClassRepo, mockMediumRepo, mockTissueRepo,
                mockBioStateRepo,
                mockSlotRegionService, mockBioRiskService, mockWorkService,
                mockExternalBarcodeValidation, mockDonorNameValidation, mockExternalNameValidation,
                mockReplicateValidator, mockVisiumLpBarcodeValidation, mockXeniumBarcodeValidator, thicknessSanisiser));
    }

    private void mockValidator(Validator<String> validator) {
        final String desc;
        if (validator==mockDonorNameValidation) {
            desc = "Bad donor name: ";
        } else if (validator==mockExternalBarcodeValidation) {
            desc = "Bad external barcode: ";
        } else if (validator==mockExternalNameValidation) {
            desc = "Bad external name: ";
        } else if (validator==mockVisiumLpBarcodeValidation) {
            desc = "Bad visium barcode: ";
        } else if (validator==mockXeniumBarcodeValidator) {
            desc = "Bad xenium barcode: ";
        } else if (validator==mockReplicateValidator) {
            desc = "Bad replicate: ";
        } else {
            desc = "Bad string: ";
        }
        when(validator.validate(any(), any())).then(invocation -> {
            String name = invocation.getArgument(0);
            if (name.startsWith("!")) {
                Consumer<String> problemConsumer = invocation.getArgument(1);
                problemConsumer.accept(desc + name);
                return false;
            }
            return true;
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testValidate(boolean valid) {
        UCMap<Donor> donors = UCMap.from(Donor::getDonorName, EntityFactory.getDonor());
        UCMap<LabwareType> lwTypes = UCMap.from(LabwareType::getName, EntityFactory.getTubeType());
        UCMap<Tissue> tissues = UCMap.from(Tissue::getExternalName, EntityFactory.getTissue());
        UCMap<Sample> samples = UCMap.from(sam -> sam.getTissue().getExternalName(), EntityFactory.getSample());
        UCMap<SlotRegion> regions = UCMap.from(SlotRegion::getName, EntityFactory.getSlotRegion());
        UCMap<BioRisk> risks = UCMap.from(BioRisk::getCode, new BioRisk(1, "risk1"));
        Work work = new Work();
        work.setId(16);
        when(mockWorkService.validateUsableWork(anyCollection(), anyString())).thenReturn(work);

        final String workNumber = "SGP1";
        var validation = makeValidation(new SectionRegisterRequest(null, workNumber));
        final String problem = "Things are bad.";
        if (valid) {
            doNothing().when(validation).checkEmpty();
        } else {
            doAnswer(invocation -> validation.getProblems().add(problem))
                    .when(validation).checkEmpty();
        }
        doReturn(donors).when(validation).validateDonors();
        doReturn(lwTypes).when(validation).validateLabwareTypes();
        doNothing().when(validation).validateBarcodes(lwTypes);
        doReturn(tissues).when(validation).validateTissues(any());
        doReturn(samples).when(validation).validateSamples(any());
        doReturn(regions).when(validation).validateRegions();
        doReturn(risks).when(validation).validateBioRisks();

        ValidatedSections vs = validation.validate();

        verify(mockWorkService).validateUsableWork(validation.getProblems(), workNumber);

        if (valid) {
            assertNotNull(vs);
            assertSame(donors, vs.donorMap());
            assertSame(lwTypes, vs.labwareTypes());
            assertSame(samples, vs.sampleMap());
            assertSame(regions, vs.slotRegionMap());
            assertSame(risks, vs.bioRiskMap());
            assertThat(validation.getProblems()).isEmpty();
            assertSame(work, vs.work());
            validation.throwError();
        } else {
            assertNull(vs);
            // Test problems
            assertThat(validation.getProblems()).contains(problem);
            // Test throwError
            ValidationException ex = assertThrows(ValidationException.class, validation::throwError);
            assertThat(ex).hasMessage("The section register request could not be validated.");
            assertEquals(Set.of(problem), ex.getProblems());
        }
    }

    @ParameterizedTest
    @MethodSource("checkEmptyArgs")
    public void testCheckEmpty(SectionRegisterRequest request, String expectedProblem) {
        var validation = makeValidation(request);
        validation.checkEmpty();
        if (expectedProblem==null) {
            assertThat(validation.getProblems()).isEmpty();
        } else {
            assertThat(validation.getProblems()).containsOnly(expectedProblem);
        }
    }

    static Stream<Arguments> checkEmptyArgs() {
        final String workNumber = "SGP1";
        return Stream.of(
                Arguments.of(new SectionRegisterRequest(List.of(), workNumber), "No labware specified in request."),
                Arguments.of(new SectionRegisterRequest(List.of(new SectionRegisterLabware()), workNumber),
                        "Labware requested without contents."),
                Arguments.of(new SectionRegisterRequest(List.of(new SectionRegisterLabware(
                        "X1", "Thing", List.of(new SectionRegisterContent())
                )), workNumber), null)
        );
    }

    @ParameterizedTest
    @MethodSource("validateDonorsArgs")
    public void testValidateDonors(Object requestObj, Object expectedProblemObj, Object expectedDonorsObj,
                                   Object existingDonorsObj, Object existingSpeciesObj) {
        Collection<Donor> donors = objToCollection(existingDonorsObj);
        when(mockDonorRepo.findAllByDonorNameIn(any())).then(findAllAnswer(donors, Donor::getDonorName));
        Collection<Species> species = objToCollection(existingSpeciesObj);
        when(mockSpeciesRepo.findAllByNameIn(any())).then(findAllAnswer(species, Species::getName));

        mockValidator(mockDonorNameValidation);

        var validation = makeValidation(requestObj);

        UCMap<Donor> donorMap = validation.validateDonors();
        assertThat(validation.getProblems()).containsExactlyInAnyOrderElementsOf(objToCollection(expectedProblemObj));
        assertEquals(objToUCMap(expectedDonorsObj, Donor::getDonorName), donorMap);
    }

    /** @see #testValidateDonors */
    static Stream<Arguments> validateDonorsArgs() {
        Species human = EntityFactory.getHuman();
        Species hamster = new Species(2, "Hamster");
        Species dodo = new Species(3, "Dodo");
        dodo.setEnabled(false);
        List<Species> knownSpecies = List.of(human, hamster, dodo);

        Donor donor1 = new Donor(1, "DONOR1", LifeStage.adult, human);
        List<Donor> knownDonors = List.of(donor1);

        return Stream.of(
                // Successful, one existing and two new donors
                Arguments.of(
                        List.of(new SectionRegisterContent("DONOR1", LifeStage.adult, Species.HUMAN_NAME),
                                new SectionRegisterContent("DONOR1", LifeStage.adult, Species.HUMAN_NAME),
                                new SectionRegisterContent("DONOR2", LifeStage.fetal, Species.HUMAN_NAME),
                                new SectionRegisterContent("DONOR2", LifeStage.fetal, Species.HUMAN_NAME),
                                new SectionRegisterContent("DONOR3", LifeStage.paediatric, "Hamster"),
                                new SectionRegisterContent("DONOR4", null, Species.HUMAN_NAME),
                                new SectionRegisterContent("DONOR4", null, Species.HUMAN_NAME)),
                        null, List.of(new Donor(null, "DONOR2", LifeStage.fetal, human),
                                new Donor(null, "DONOR3", LifeStage.paediatric, hamster),
                                new Donor(null, "DONOR4", null, human),
                                donor1), knownDonors, knownSpecies
                ),

                // Various individual problems
                Arguments.of(
                        new SectionRegisterContent(null, LifeStage.adult, Species.HUMAN_NAME),
                        "Missing donor identifier.", null, knownDonors, knownSpecies
                ),
                Arguments.of(
                        new SectionRegisterContent("", LifeStage.adult, Species.HUMAN_NAME),
                        "Missing donor identifier.", null, knownDonors, knownSpecies
                ),
                Arguments.of(
                        new SectionRegisterContent("DONOR1", null, Species.HUMAN_NAME),
                        "Wrong life stage given for existing donor DONOR1", donor1, knownDonors, knownSpecies
                ),
                Arguments.of(
                        new SectionRegisterContent("DONOR1", LifeStage.adult, null),
                        "Missing species.", donor1, knownDonors, knownSpecies
                ),
                Arguments.of(
                        new SectionRegisterContent("DONOR2", LifeStage.adult, "Unicorn"),
                        "Unknown species: \"Unicorn\"", new Donor(null, "DONOR2", LifeStage.adult, null),
                        knownDonors, knownSpecies
                ),
                Arguments.of(
                        new SectionRegisterContent("DONOR2", LifeStage.adult, "Dodo"),
                        "Species not enabled: [Dodo]", new Donor(null, "DONOR2", LifeStage.adult, dodo),
                        knownDonors, knownSpecies
                ),
                Arguments.of(
                        List.of(
                            new SectionRegisterContent("DONOR1", LifeStage.adult, Species.HUMAN_NAME),
                            new SectionRegisterContent("DONOR2", LifeStage.adult, Species.HUMAN_NAME),
                            new SectionRegisterContent("Donor2", LifeStage.fetal, Species.HUMAN_NAME)
                        ), "Multiple different life stages specified for donor DONOR2",
                        List.of(donor1, new Donor(null, "DONOR2", LifeStage.adult, human)),
                        knownDonors, knownSpecies
                ),
                Arguments.of(
                        List.of(
                                new SectionRegisterContent("DONOR2", LifeStage.adult, Species.HUMAN_NAME),
                                new SectionRegisterContent("Donor2", LifeStage.adult, "hamster")
                        ), "Multiple different species specified for donor DONOR2",
                        new Donor(null, "DONOR2", LifeStage.adult, human),
                        knownDonors, knownSpecies
                ),
                Arguments.of(
                        new SectionRegisterContent("Donor1", LifeStage.paediatric, Species.HUMAN_NAME),
                        "Wrong life stage given for existing donor DONOR1", donor1, knownDonors, knownSpecies
                ),
                Arguments.of(
                        new SectionRegisterContent("Donor1", LifeStage.adult, "Hamster"),
                        "Wrong species given for existing donor DONOR1", donor1, knownDonors, knownSpecies
                ),
                Arguments.of(
                        List.of(new SectionRegisterContent("DONOR2", null, ""),
                                new SectionRegisterContent("Donor2", LifeStage.adult, null),
                                new SectionRegisterContent("donor2", null, Species.HUMAN_NAME)),
                        List.of("Missing species.", "Multiple different life stages specified for donor DONOR2"),
                        new Donor(null, "DONOR2", null, human),
                        knownDonors, knownSpecies
                ),

                // many problems
                Arguments.of(
                        List.of(new SectionRegisterContent("DONOR1", LifeStage.paediatric, "hamster"),
                                new SectionRegisterContent("DONOR2", null, null),
                                new SectionRegisterContent(null, LifeStage.fetal, Species.HUMAN_NAME),
                                new SectionRegisterContent("Donor2", LifeStage.adult, Species.HUMAN_NAME),
                                new SectionRegisterContent("DONOR2", LifeStage.fetal, "Hamster"),
                                new SectionRegisterContent("DONOR3", LifeStage.fetal, "Unicorn")),
                        List.of("Wrong life stage given for existing donor DONOR1",
                                "Wrong species given for existing donor DONOR1",
                                "Missing species.", "Missing donor identifier.",
                                "Multiple different life stages specified for donor DONOR2",
                                "Multiple different species specified for donor DONOR2",
                                "Unknown species: \"Unicorn\""
                        ),
                        List.of(donor1, new Donor(null, "DONOR2", null, human),
                                new Donor(null, "DONOR3", LifeStage.fetal, null)),
                        knownDonors, knownSpecies
                )
        );
    }

    @ParameterizedTest
    @MethodSource("validateLabwareTypesArgs")
    public void testValidateLabwareTypes(List<?> requestData, Object expectedProblemsObj, Object expectedLwTypesObj,
                                     Object knownLabwareTypesObj) {
        Collection<LabwareType> knownLwTypes = objToCollection(knownLabwareTypesObj);
        when(mockLwTypeRepo.findAllByNameIn(any())).then(findAllAnswer(knownLwTypes, LabwareType::getName));
        SectionRegisterRequest request = labwareTypeRequest(requestData);
        SectionRegisterValidation validation = makeValidation(request);

        UCMap<LabwareType> lwTypes = validation.validateLabwareTypes();

        UCMap<LabwareType> expectedLwTypes = objToUCMap(expectedLwTypesObj, LabwareType::getName);
        Collection<String> expectedProblems = objToCollection(expectedProblemsObj);

        assertThat(validation.getProblems()).containsExactlyInAnyOrderElementsOf(expectedProblems);
        assertEquals(expectedLwTypes, lwTypes);
    }

    static Stream<Arguments> validateLabwareTypesArgs() {
        LabwareType lt6 = EntityFactory.makeLabwareType(2, 3);
        lt6.setName("lt6");
        LabwareType lt1 = EntityFactory.makeLabwareType(1,1);
        lt1.setName("lt1");
        List<LabwareType> lts = List.of(lt1, lt6);
        Address A1 = new Address(1,1);
        Address B3 = new Address(2, 3);
        Address C4 = new Address(3, 4);
        return Stream.of(
                Arguments.of(List.of("LT6", A1, B3, "Lt1", A1, "lt1", A1),
                        null, lts, lts),

                Arguments.of(Arrays.asList(null, A1),
                        "Missing labware type.", null, lts),
                Arguments.of(List.of("", A1),
                        "Missing labware type.", null, lts),
                Arguments.of(List.of("lt404", A1),
                        "Unknown labware type \"lt404\"", null, lts),
                Arguments.of(Arrays.asList("lt1", A1, null),
                        "Missing slot address.", lt1, lts),
                Arguments.of(List.of("lt6", A1, B3, "lt1", A1, B3, "LT6", B3, C4),
                        List.of("Invalid address B3 in labware type lt1.",
                                "Invalid address C4 in labware type lt6."),
                        lts, lts),
                Arguments.of(Arrays.asList("", A1, "lt404", A1, "lt1", B3, null),
                        List.of("Missing labware type.", "Missing slot address.",
                                "Unknown labware type \"lt404\"",
                                "Invalid address B3 in labware type lt1."),
                        lt1, lts)
        );
    }
    @ParameterizedTest
    @MethodSource("validateBarcodesArgs")
    public void testValidateBarcodes(List<String> xbs, List<String> pbs, String ltName, List<String> expectedProblems,
                                      UCMap<LabwareType> lwTypes) {
        SectionRegisterRequest request = new SectionRegisterRequest(xbs.stream()
                .map(bc -> new SectionRegisterLabware(bc, ltName, null))
                .collect(toList()), "SGP1");
        if (pbs!=null) {
            var reqIter = request.getLabware().iterator();
            for (String pb : pbs) {
                reqIter.next().setPreBarcode(pb);
            }
        }
        mockValidator(mockExternalBarcodeValidation);
        mockValidator(mockVisiumLpBarcodeValidation);
        mockValidator(mockXeniumBarcodeValidator);

        SectionRegisterValidation validation = makeValidation(request);
        validation.validateBarcodes(lwTypes);
        verify(validation).checkForPrebarcodeMismatch();
        assertThat(validation.getProblems()).containsExactlyInAnyOrderElementsOf(nullToEmpty(expectedProblems));
    }

    static Stream<Arguments> validateBarcodesArgs() {
        LabwareType normalLt = new LabwareType(1, "lt", 1, 1, null, false);
        LabwareType visiumLt = new LabwareType(2, "Visium LP", 1, 1, null, true);
        LabwareType xeniumLt = new LabwareType(3, "Xenium", 1, 1, null, true);
        UCMap<LabwareType> lwTypes = UCMap.from(LabwareType::getName, normalLt, visiumLt, xeniumLt);
        return Arrays.stream(new Object[][] {
                {List.of("Alpha","Beta"), null, "lt"},
                {List.of("Gamma"), List.of("Delta"), "Visium LP"},
                {List.of("Delta"), List.of("Epsilon"), "Xenium"},
                {List.of("!Bad"), null, "lt", List.of("Bad external barcode: !Bad")},
                {List.of(""), null, "lt",List.of("Missing external barcode.")},
                {List.of("Alpha"), List.of("Beta"), "lt", List.of("Prebarcode not expected for labware type lt.")},
                {List.of("!Alpha"), null, "Visium LP", List.of("Bad visium barcode: !Alpha")},
                {List.of("Alpha"), List.of("!Beta"), "Visium LP", List.of("Bad visium barcode: !Beta")},
                {List.of("Alpha"), List.of("!Beta"), "Xenium", List.of("Bad xenium barcode: !Beta")},
                {List.of("Alpha", "Beta", "ALPHA"), null, "lt", List.of("Repeated barcode: [ALPHA]")},
                {List.of("Alpha", "Beta", "Gamma", "Delta", "Epsilon"), List.of("Alpha", "Alaska", "", "Beta", "ALASKA"), "xenium",
                        List.of("Entries referring to the same labware should have the same external slide ID and the " +
                                "same Xenium barcode. Entries referring to different labware should have different " +
                                "external slide ID and different Xenium barcode.", "Repeated barcodes: [Beta, ALASKA]")},
        }).map(arr -> {
            Object[] arr2 = Arrays.copyOf(arr, 5);
            arr2[4] = lwTypes;
            return Arguments.of(arr2);
        });
    }

    @ParameterizedTest
    @CsvSource({
            "MYBC,,",
            "mybc,seen,Repeated barcode{s}",
            "stan-abc,,Invalid external barcode prefix",
            "sto-abc,,Invalid external barcode prefix",
            "mybc,existsExternal,External barcode{s} already used",
            "mybc,exists,Labware barcode{s} already used",
    })
    public void testFindBarcodeProblem(String barcode, String mode, String expectedProblem) {
        Set<String> seen = new HashSet<>(1);
        if (mode!=null) {
            String upper = barcode.toUpperCase();
            switch (mode) {
                case "seen": seen.add(upper); break;
                case "existsExternal": when(mockLwRepo.existsByExternalBarcode(upper)).thenReturn(true); break;
                case "exists": when(mockLwRepo.existsByBarcode(upper)).thenReturn(true); break;
            }
        }
        SectionRegisterValidation validation = makeValidation(null);
        assertEquals(expectedProblem, validation.findBarcodeProblem(barcode, seen));
    }

    @ParameterizedTest
    @MethodSource("validateTissuesArgs")
    void testValidateTissues(ValidateTissueTestData testData) {
        when(mockHmdmcRepo.findAllByHmdmcIn(any())).then(findAllAnswer(testData.hmdmcs, Hmdmc::getHmdmc));
        when(mockTissueTypeRepo.findAllByNameIn(any())).then(findAllAnswer(testData.tissueTypes, TissueType::getName));
        when(mockFixativeRepo.findAllByNameIn(any())).then(findAllAnswer(testData.fixatives, Fixative::getName));
        when(mockMediumRepo.findAllByNameIn(any())).then(findAllAnswer(testData.mediums, Medium::getName));
        when(mockTissueRepo.findAllByExternalNameIn(any())).then(findAllAnswer(testData.existingTissues, Tissue::getExternalName));
        when(mockCellClassRepo.findAllByNameIn(any())).then(findAllAnswer(testData.cellClasses, CellClass::getName));
        mockValidator(mockExternalNameValidation);
        mockValidator(mockReplicateValidator);

        UCMap<Donor> donorMap = UCMap.from(coalesce(testData.donors, List.of()), Donor::getDonorName);

        SectionRegisterValidation validation = makeValidation(testData.getRequest());
        UCMap<Tissue> tissues = validation.validateTissues(donorMap);
        UCMap<Tissue> expectedTissues = UCMap.from(coalesce(testData.expectedTissues, List.of()), Tissue::getExternalName);

        assertThat(validation.getProblems()).containsExactlyInAnyOrderElementsOf(testData.expectedProblems);
        if (testData.expectedProblems.isEmpty()) {
            assertEquals(expectedTissues, tissues);
        }
    }

    /** @see #testValidateTissues */
    static Stream<ValidateTissueTestData> validateTissuesArgs() {
        final Hmdmc hmdmc1 = new Hmdmc(1, "2021/01");
        final Hmdmc hmdmc2 = new Hmdmc(2, "2021/02");
        final Hmdmc hmdmc3 = new Hmdmc(3, "2021/03");
        final Hmdmc hmdmc4 = new Hmdmc(4, "2021/04");
        hmdmc3.setEnabled(false);
        hmdmc4.setEnabled(false);
        final CellClass cellClass = EntityFactory.getCellClass();
        List<Hmdmc> hmdmcs = List.of(hmdmc1, hmdmc2, hmdmc3, hmdmc4);
        final TissueType ARM = makeTissueType(1, "Arm", "ARM");
        final TissueType LEG = makeTissueType(2, "Leg", "LEG");
        LEG.getSpatialLocations().get(1).setEnabled(false);
        final TissueType TAIL = makeTissueType(3, "Tail", "TAIL");
        TAIL.setEnabled(false);
        List<TissueType> tissueTypes = List.of(ARM, LEG, TAIL);
        final Fixative fixNone = new Fixative(10, "None");
        final Fixative fix = new Fixative(11, "Formalin");
        List<Fixative> fixatives = List.of(fixNone, fix);
        final Medium mediumNone = new Medium(20, "None");
        final Medium medium = new Medium(21, "Butter");
        List<Medium> mediums = List.of(mediumNone, medium);
        List<CellClass> cellClasses = List.of(cellClass);
        final Donor DONOR1 = new Donor(1, "DONOR1", LifeStage.adult, EntityFactory.getHuman());
        final Donor DONOR2 = new Donor(null, "DONOR2", LifeStage.fetal, null);
        List<Donor> donors = List.of(DONOR1, DONOR2);
        Supplier<ValidateTissueTestData> testData = () ->
                new ValidateTissueTestData(hmdmcs, tissueTypes, fixatives, mediums, cellClasses, donors);

        return Stream.of(
                // Good request
                testData.get()
                        .content("EXT1", "4", "Arm", 2, "Donor1", "None", "None", "2021/01", Species.HUMAN_NAME, "tissue")
                        .content("EXT2", "5", "Leg", 1, "Donor1", "butter", "Formalin", "2021/02", Species.HUMAN_NAME, "tissue")
                        .content("EXT3", "5", "Leg", 1, "Donor2", "butter", "Formalin", null, "hamster", "tissue")
                        .tissues(new Tissue(null, "EXT1", "4", ARM.getSpatialLocations().get(1), DONOR1, mediumNone, fixNone, cellClass, hmdmc1, null, null),
                                new Tissue(null, "EXT2", "5", LEG.getSpatialLocations().get(0), DONOR1, medium, fix, cellClass, hmdmc2, null, null),
                                new Tissue(null, "EXT3", "5", LEG.getSpatialLocations().get(0), DONOR2, medium, fix, cellClass, null, null, null)),

                // Single problems
                testData.get()
                        .content("EXT1", "4", "ARM", 1, "Donor1", "None", "None", null, Species.HUMAN_NAME, "tissue")
                        .problem("Missing HuMFre number."),
                testData.get()
                        .content("EXT1", "4", "ARM", 1, "Donor1", "None", "None", "", Species.HUMAN_NAME, "tissue")
                        .content("EXT2", "4", "ARM", 1, "Donor1", "None", "None", "2021/01", "", "tissue")
                        .problem("Missing HuMFre number."),
                testData.get()
                        .content("EXT1", "4", "ARM", 1, "Donor1", "None", "None", "2021/404", Species.HUMAN_NAME, "tissue")
                        .content("EXT2", "4", "ARM", 1, "Donor1", "None", "None", "2021/405", Species.HUMAN_NAME, "tissue")
                        .content("EXT3", "4", "ARM", 1, "Donor1", "None", "None", "2021/405", Species.HUMAN_NAME, "tissue")
                        .problem("Unknown HuMFre numbers: [2021/404, 2021/405]"),
                testData.get()
                        .content("EXT1", "4", "ARM", 1, "Donor2", "None", "None", "2021/01", "Hamster", "tissue")
                        .problem("Unexpected HuMFre number received for non-human tissue."),
                testData.get()
                        .content("EXT1", "4", "ARM", 1, "Donor2", "None", "None", "2021/03", Species.HUMAN_NAME, "tissue")
                        .content("EXT2", "4", "ARM", 1, "Donor2", "None", "None", "2021/04", Species.HUMAN_NAME, "tissue")
                        .problem("HuMFre number not enabled: [2021/03, 2021/04]"),
                testData.get()
                        .content(null, "4", "ARM", 1, "Donor1", "None", "None", "2021/01", Species.HUMAN_NAME, "tissue")
                        .problem("Missing external identifier."),
                testData.get()
                        .content("", "4", "ARM", 1, "Donor1", "None", "None", "2021/01", Species.HUMAN_NAME, "tissue")
                        .problem("Missing external identifier."),
                testData.get()
                        .content("!DN", "4", "ARM", 1, "Donor1", "none", "none", "2021/01", Species.HUMAN_NAME, "tissue")
                        .problem("Bad external name: !DN"),
                testData.get()
                        .content("EXT1", "4", "ARM", 1, "Donor1", "None", "None", "2021/01", Species.HUMAN_NAME, "tissue")
                        .content("EXT1", "5", "ARM", 1, "Donor1", "None", "None", "2021/01", Species.HUMAN_NAME, "tissue")
                        .content("EXT2", "4", "ARM", 1, "Donor1", "None", "None", "2021/01", Species.HUMAN_NAME, "tissue")
                        .content("EXT2", "5", "ARM", 1, "Donor1", "None", "None", "2021/01", Species.HUMAN_NAME, "tissue")
                        .content("EXT3", "5", "ARM", 1, "Donor1", "None", "None", "2021/01", Species.HUMAN_NAME, "tissue")
                        .problem("Repeated external identifiers: [EXT1, EXT2]"),
                testData.get()
                        .content("TISSUE1", "4", "ARM", 1, "Donor1", "none", "none", "2021/01", Species.HUMAN_NAME, "tissue")
                        .content("TISSUE2", "4", "ARM", 1, "Donor1", "none", "none", "2021/01", Species.HUMAN_NAME, "tissue")
                        .content("TISSUE3", "4", "ARM", 1, "Donor1", "none", "none", "2021/01", Species.HUMAN_NAME, "tissue")
                        .existing(new Tissue(1, "TISSUE1", "3", ARM.getSpatialLocations().get(0), DONOR1, medium, fix, null, hmdmcs.get(0), null, null),
                                new Tissue(2, "TISSUE2", "3", ARM.getSpatialLocations().get(0), DONOR1, medium, fix, null, hmdmcs.get(0), null, null))
                        .problem("External identifiers already in use: [TISSUE1, TISSUE2]"),
                testData.get()
                        .content("EXT1", "4", null, 1, "Donor1", "None", "None", "2021/01", Species.HUMAN_NAME, "tissue")
                        .problem("Missing tissue type."),
                testData.get()
                        .content("EXT1", "4", "Squirrel", 1, "Donor1", "None", "None", "2021/01", Species.HUMAN_NAME, "tissue")
                        .problem("Unknown tissue type: [Squirrel]"),
                testData.get()
                        .content("EXT1", "4", "ARM", null, "Donor1", "None", "None", "2021/01", Species.HUMAN_NAME, "tissue")
                        .problem("Missing spatial location."),
                testData.get()
                        .content("EXT1", "4", "ARM", 5, "Donor1", "None", "None", "2021/01", Species.HUMAN_NAME, "tissue")
                        .content("EXT2", "4", "LEG", 3, "Donor1", "None", "None", "2021/01", Species.HUMAN_NAME, "tissue")
                        .problem("Unknown spatial locations: [5 for Arm, 3 for Leg]"),
                testData.get()
                        .content("EXT1", null, "ARM", 1, "Donor1", "None", "None", "2021/01", Species.HUMAN_NAME, "tissue")
                        .problem("Missing replicate number."),

                testData.get()
                        .content("EXT1", "!-4", "ARM", 1, "Donor1", "None", "None", "2021/01", Species.HUMAN_NAME, "tissue")
                        .problem("Bad replicate: !-4"),
                testData.get()
                        .content("EXT1", "4", "ARM", 1, "Donor1", "Custard", "None", "2021/01", Species.HUMAN_NAME, "tissue")
                        .content("EXT2", "4", "ARM", 1, "Donor1", "Jelly", "None", "2021/01", Species.HUMAN_NAME, "tissue")
                        .problem("Unknown mediums: [Custard, Jelly]"),
                testData.get()
                        .content("EXT1", "4", "ARM", 1, "Donor1", "None", "Glue", "2021/01", Species.HUMAN_NAME, "tissue")
                        .content("EXT2", "4", "ARM", 1, "Donor1", "None", "Stapler", "2021/01", Species.HUMAN_NAME, "tissue")
                        .problem("Unknown fixatives: [Glue, Stapler]"),
                testData.get()
                        .content("EXT1", "4", "TAIL", 1, "Donor1", "None", "None", "2021/01", Species.HUMAN_NAME, "tissue")
                        .problem("Tissue type is disabled: [Tail]"),
                testData.get()
                        .content("EXT1", "4", "LEG", 2, "Donor1", "None", "None", "2021/01", Species.HUMAN_NAME, "tissue")
                        .problem("Disabled spatial location: [2 for Leg]"),

                // Mixed problems
                testData.get()
                        .content(null, null, null, null, null, null, null, null, Species.HUMAN_NAME, "tissue")
                        .problems("Missing external identifier.", "Missing replicate number.", "Missing tissue type.", "Missing spatial location.", "Missing medium.",
                                "Missing fixative.", "Missing HuMFre number."),
                testData.get()
                        .content("!X11", "!-1", "Squirrel", 2, null, "Custard", "Stapler", "2021/404", null, "tissue")
                        .problems("Bad external name: !X11", "Bad replicate: !-1", "Unknown tissue type: [Squirrel]", "Unknown medium: [Custard]",
                                "Unknown fixative: [Stapler]", "Unknown HuMFre number: [2021/404]")

        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"xenium", "non-xenium", "ok"})
    void testCheckForPrebarcodeMismatch(String mode) {
        String lt = mode.equalsIgnoreCase("xenium") ? "xenium" : "plate";
        List<SectionRegisterLabware> srls = List.of(
                new SectionRegisterLabware("xb1", lt, List.of()),
                new SectionRegisterLabware("xb2", lt, List.of())
        );
        srls.get(0).setPreBarcode("bc1");
        srls.get(1).setPreBarcode(mode.equalsIgnoreCase("ok") ? "bc2" : "BC1");
        var val = makeValidation(srls);
        val.checkForPrebarcodeMismatch();
        String expectedProblem = switch (mode) {
            case "xenium" -> "Entries referring to the same labware should have the same external slide ID and " +
                    "the same Xenium barcode. Entries referring to different labware should have different external " +
                    "slide ID and different Xenium barcode.";
            case "non-xenium" -> "Entries referring to the same labware should have the same external slide ID and " +
                    "the same prebarcode. Entries referring to different labware should have different external " +
                    "slide ID and different prebarcode.";
            default -> null;
        };
        assertProblem(val.getProblems(), expectedProblem);
    }

    @ParameterizedTest
    @MethodSource("validateSamplesArgs")
    public void testValidateSamples(Object contentsObj, UCMap<Tissue> tissueMap, Object expectedProblemsObj,
                                    Object expectedSamplesObj, BioState bs) {
        when(mockBioStateRepo.findByName("Tissue")).thenReturn(Optional.ofNullable(bs));

        SectionRegisterValidation val = makeValidation(contentsObj);

        UCMap<Sample> sampleMap = val.validateSamples(tissueMap);

        Collection<String> expectedProblems = objToCollection(expectedProblemsObj);
        assertThat(val.getProblems()).containsExactlyInAnyOrderElementsOf(expectedProblems);
        if (expectedProblems.isEmpty()) {
            assertEquals(objToUCMap(expectedSamplesObj, (Sample sam) -> sam.getTissue().getExternalName()), sampleMap);
        }
    }

    static Stream<Arguments> validateSamplesArgs() {
        BioState bs = new BioState(1, "Tissue");
        Tissue tissue1 = new Tissue();
        tissue1.setExternalName("TISSUE1");
        UCMap<Tissue> tissues = UCMap.from(Tissue::getExternalName, tissue1);

        return Stream.of(
                Arguments.of(content("TISSUE1", 2, 4), tissues, null,
                        new Sample(null, 2, tissue1, bs), bs),
                Arguments.of(content("TISSUE1", 2, null), tissues, null,
                        new Sample(null, 2, tissue1, bs), bs),

                Arguments.of(content("TISSUE1", null, 4), tissues,
                        "Missing section number.", null, bs),
                Arguments.of(content("TISSUE1", -2, 4), tissues,
                        "Section number cannot be negative.", null, bs),
                Arguments.of(content("TISSUE1", 4, -3), tissues,
                        "Value outside the expected bounds for thickness: -3", null, bs),
                Arguments.of(content("TISSUE1", 2, 4), tissues,
                        "Bio state \"Tissue\" not found.", null, null),

                Arguments.of(content("TISSUE1", null, -1), tissues,
                        List.of("Missing section number.", "Value outside the expected bounds for thickness: -1",
                                "Bio state \"Tissue\" not found."), null, null)
        );
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testValidateBioRisks() {
        UCMap<BioRisk> risks = UCMap.from(BioRisk::getCode, new BioRisk(1, "risk1"));
        when(mockBioRiskService.loadAndValidateBioRisks(any(), any(), any(), any())).thenReturn(risks);
        SectionRegisterRequest request = new SectionRegisterRequest(List.of(
                srlWithBioRisks("risk1", null, "risk2"),
                srlWithBioRisks("risk2")
        ), "work1");
        SectionRegisterValidation val = makeValidation(request);
        assertSame(risks, val.validateBioRisks());
        ArgumentCaptor<Stream<SectionRegisterContent>> streamCaptor = ArgumentCaptor.forClass(Stream.class);
        ArgumentCaptor<Function<SectionRegisterContent, String>> getterCaptor = ArgumentCaptor.forClass(Function.class);
        ArgumentCaptor<BiConsumer<SectionRegisterContent, String>> setterCaptor = ArgumentCaptor.forClass(BiConsumer.class);
        verify(mockBioRiskService).loadAndValidateBioRisks(same(val.getProblems()), streamCaptor.capture(),
                getterCaptor.capture(), setterCaptor.capture());
        assertThat(streamCaptor.getValue().map(getterCaptor.getValue())).containsExactly("risk1", null, "risk2", "risk2");
        BiConsumer<SectionRegisterContent, String> setter = setterCaptor.getValue();
        SectionRegisterContent src = new SectionRegisterContent();
        setter.accept(src, "v1");
        assertEquals("v1", src.getBioRiskCode());
    }

    private static SectionRegisterLabware srlWithBioRisks(String... codes) {
        List<SectionRegisterContent> contents = Arrays.stream(codes)
                .map(code -> {
                    SectionRegisterContent src = new SectionRegisterContent();
                    src.setBioRiskCode(code);
                    return src;
                })
                .toList();
        return new SectionRegisterLabware(null, null, contents);
    }

    @ParameterizedTest
    @CsvSource({"false,false,false",
            "false,true,false",
            "true,true,false",
            "false,true,true",
    })
    public void testValidateRegions(boolean anyMissing, boolean anyPresent, boolean anyInvalid) {
        final Address A1 = new Address(1,1);
        final Address A2 = new Address(1,2);
        List<SectionRegisterContent> src1;
        if (anyPresent) {
            src1 = List.of(
                    content(A1, "SR1"),
                    content(A2, "SR2"),
                    content(A2, null),
                    content(null, "SR3")
            );
        } else {
            src1 = List.of();
        }
        SectionRegisterRequest req = new SectionRegisterRequest(List.of(
                new SectionRegisterLabware("A", "", src1),
                new SectionRegisterLabware("B", "", List.of())
        ), null);
        SectionRegisterValidation val = makeValidation(req);

        UCMap<SlotRegion> regions;
        if (anyPresent) {
            regions = UCMap.from(SlotRegion::getName, EntityFactory.getSlotRegion());
            doReturn(regions).when(mockSlotRegionService).loadSlotRegionMap(true);
        } else {
            regions = null;
        }
        doReturn(false).when(val).anyMissingRegions(any());
        if (anyMissing) {
            doReturn(true).when(val).anyMissingRegions(req.getLabware().get(1));
        }
        doReturn(regions).when(mockSlotRegionService).loadSlotRegionMap(true);

        if (anyInvalid) {
            doReturn(Set.of("Bad regions")).when(mockSlotRegionService).validateSlotRegions(any(), any());
        } else if (anyPresent) {
            doReturn(Set.of()).when(mockSlotRegionService).validateSlotRegions(any(), any());
        }

        if (anyPresent) {
            assertSame(regions, val.validateRegions());
        } else {
            assertThat(val.validateRegions()).isEmpty();
        }
        Set<String> expectedProblems = new HashSet<>(2);
        if (anyMissing) {
            expectedProblems.add("Slot regions must be specified for each section in a shared slot.");
        }
        if (anyInvalid) {
            expectedProblems.add("Bad regions");
        }
        assertThat(val.getProblems()).containsExactlyInAnyOrderElementsOf(expectedProblems);

        req.getLabware().forEach(srl -> verify(val).anyMissingRegions(srl));
        if (anyPresent) {
            ArgumentCaptor<Stream<Map.Entry<Address, String>>> captor = Matchers.streamCaptor();
            verify(mockSlotRegionService, times(2)).validateSlotRegions(same(regions), captor.capture());
            Stream<Map.Entry<Address, String>> elements = captor.getAllValues().get(0);
            assertThat(elements).containsExactly(Map.entry(A1, "SR1"), Map.entry(A2, "SR2"));
            elements = captor.getValue();
            assertThat(elements).isEmpty();
        } else {
            verifyNoInteractions(mockSlotRegionService);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false,true})
    public void testAnyMissingRegions(boolean anyMissing) {
        final Address A1 = new Address(1,1);
        final Address A2 = new Address(1,2);
        List<SectionRegisterContent> contents = List.of(
                content(A1, null),
                content(A2, "Alpha"),
                content(A2, anyMissing ? "" : "Beta")
        );
        SectionRegisterLabware srl = new SectionRegisterLabware();
        srl.setContents(contents);
        var val = makeValidation(srl);
        when(mockSlotRegionService.anyMissingRegions(any())).thenReturn(anyMissing);
        assertEquals(anyMissing, val.anyMissingRegions(srl));
        ArgumentCaptor<Stream<Map.Entry<Address, String>>> captor = Matchers.streamCaptor();
        verify(mockSlotRegionService).anyMissingRegions(captor.capture());
        assertThat(captor.getValue()).containsExactly(simpleEntry(A1, null), simpleEntry(A2, "Alpha"), simpleEntry(A2, anyMissing ? "" : "Beta"));
    }

    private static SectionRegisterContent content(String extName, Integer section, Integer thickness) {
        SectionRegisterContent content = new SectionRegisterContent();
        content.setExternalIdentifier(extName);
        content.setSectionNumber(section);
        content.setSectionThickness(thickness==null ? null : thickness.toString());
        return content;
    }


    private static SectionRegisterContent content(Address address, String regionName) {
        SectionRegisterContent src = new SectionRegisterContent();
        src.setAddress(address);
        src.setRegion(regionName);
        return src;
    }

    private static class ValidateTissueTestData {
        List<String> expectedProblems;
        List<Tissue> expectedTissues;

        SectionRegisterRequest request;
        List<SectionRegisterContent> contents;

        List<Hmdmc> hmdmcs;
        List<TissueType> tissueTypes;
        List<Fixative> fixatives;
        List<Medium> mediums;
        List<CellClass> cellClasses;
        List<Tissue> existingTissues;
        List<Donor> donors;

        public ValidateTissueTestData(List<Hmdmc> hmdmcs, List<TissueType> tissueTypes, List<Fixative> fixatives,
                                      List<Medium> mediums, List<CellClass> cellClasses, List<Donor> donors) {
            this.expectedProblems = List.of();
            this.expectedTissues = List.of();
            this.contents = new ArrayList<>();
            this.hmdmcs = hmdmcs;
            this.tissueTypes = tissueTypes;
            this.fixatives = fixatives;
            this.mediums = mediums;
            this.cellClasses = cellClasses;
            this.existingTissues = List.of();
            this.donors = donors;
        }

        public ValidateTissueTestData problem(String problem) {
            this.expectedProblems = List.of(problem);
            return this;
        }
        public ValidateTissueTestData problems(String... problems) {
            this.expectedProblems = List.of(problems);
            return this;
        }

        public ValidateTissueTestData tissues(Tissue... tissues) {
            this.expectedTissues = List.of(tissues);
            return this;
        }

        public ValidateTissueTestData existing(Tissue... tissues) {
            this.existingTissues = List.of(tissues);
            return this;
        }

        public ValidateTissueTestData content(String externalName, String replicate, String tissueType, Integer spatLoc,
                                              String donorName, String medium, String fixative, String hmdmc, String species, String cellClass) {
            SectionRegisterContent content = new SectionRegisterContent();
            content.setDonorIdentifier(donorName);
            content.setExternalIdentifier(externalName);
            content.setFixative(fixative);
            content.setHmdmc(hmdmc);
            content.setTissueType(tissueType);
            content.setReplicateNumber(replicate);
            content.setMedium(medium);
            content.setSpatialLocation(spatLoc);
            content.setSpecies(species);
            content.setCellClass(cellClass);
            this.contents.add(content);
            return this;
        }

        public SectionRegisterRequest getRequest() {
            if (request==null) {
                request = assembleRequest();
            }
            return request;
        }

        public SectionRegisterRequest assembleRequest() {
            if (contents.isEmpty()) {
                return new SectionRegisterRequest();
            }
            SectionRegisterLabware srl = new SectionRegisterLabware("X11", "lt", contents);
            return new SectionRegisterRequest(List.of(srl), "SGP1");
        }

        @Override
        public String toString() {
            return expectedProblems.toString();
        }
    }

    private static TissueType makeTissueType(Integer id, String name, String code) {
        TissueType tt = new TissueType(id, name, code);
        tt.setSpatialLocations(IntStream.range(1, 3).mapToObj(i ->
                new SpatialLocation(10*id+i, "SL"+i, i, tt))
                .collect(toList()));
        return tt;
    }

    private static SectionRegisterRequest labwareTypeRequest(Collection<?> data) {
        List<SectionRegisterLabware> srls = new ArrayList<>();
        SectionRegisterLabware current = null;
        for (Object obj : data) {
            if (obj instanceof String) {
                if (current!=null) {
                    srls.add(current);
                }
                current = new SectionRegisterLabware("external barcode", (String) obj, null);
            } else if (obj==null && current==null) {
                current = new SectionRegisterLabware("external barcode", null, null);
            } else {
                assert current != null;
                Address ad = (Address) obj;
                SectionRegisterContent content = new SectionRegisterContent();
                content.setAddress(ad);
                current.getContents().add(content);
            }
        }
        if (current!=null) {
            srls.add(current);
        }
        return new SectionRegisterRequest(srls, "SGP1");
    }

    @SuppressWarnings({"unchecked"})
    private <V> UCMap<V> objToUCMap(Object obj, Function<V, String> keyFunction) {
        return switch (obj) {
            case null -> new UCMap<>();
            case UCMap<?> ucMap -> (UCMap<V>) ucMap;
            case Map<?,?> map -> new UCMap<>((Map<String, V>) map);
            case Collection<?> collection -> ((Collection<V>) collection).stream().collect(UCMap.toUCMap(keyFunction));
            default -> UCMap.from(keyFunction, (V) obj);
        };
    }

    private <E> Answer<List<E>> findAllAnswer(Collection<E> items, Function<E, String> stringFunction) {
        if (items==null || items.isEmpty()) {
            return invocation -> List.of();
        }
        return invocation -> {
            Collection<String> strings = invocation.getArgument(0);
            Set<String> stringsUc = strings.stream().map(String::toUpperCase).collect(toSet());
            return items.stream().filter(item -> stringsUc.contains(stringFunction.apply(item).toUpperCase()))
                    .collect(toList());
        };
    }
}
