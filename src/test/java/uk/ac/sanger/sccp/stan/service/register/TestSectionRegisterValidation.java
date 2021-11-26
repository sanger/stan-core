package uk.ac.sanger.sccp.stan.service.register;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.stubbing.Answer;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.register.*;
import uk.ac.sanger.sccp.stan.service.ValidationException;
import uk.ac.sanger.sccp.stan.service.Validator;
import uk.ac.sanger.sccp.utils.UCMap;

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
import static uk.ac.sanger.sccp.utils.BasicUtils.coalesce;

/**
 * Tests {@link SectionRegisterValidation}
 * @author dr6
 */
public class TestSectionRegisterValidation {
    private DonorRepo mockDonorRepo;
    private SpeciesRepo mockSpeciesRepo;
    private LabwareTypeRepo mockLwTypeRepo;
    private LabwareRepo mockLwRepo;
    private MouldSizeRepo mockMouldSizeRepo;
    private HmdmcRepo mockHmdmcRepo;
    private TissueTypeRepo mockTissueTypeRepo;
    private FixativeRepo mockFixativeRepo;
    private MediumRepo mockMediumRepo;
    private BioStateRepo mockBioStateRepo;
    private TissueRepo mockTissueRepo;
    private Validator<String> mockExternalBarcodeValidation;
    private Validator<String> mockDonorNameValidation;
    private Validator<String> mockExternalNameValidation;
    private Validator<String> mockVisiumLpBarcodeValidation;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setup() {
        mockDonorRepo = mock(DonorRepo.class);
        mockSpeciesRepo = mock(SpeciesRepo.class);
        mockLwTypeRepo = mock(LabwareTypeRepo.class);
        mockLwRepo = mock(LabwareRepo.class);
        mockMouldSizeRepo = mock(MouldSizeRepo.class);
        mockHmdmcRepo = mock(HmdmcRepo.class);
        mockTissueTypeRepo = mock(TissueTypeRepo.class);
        mockFixativeRepo = mock(FixativeRepo.class);
        mockMediumRepo = mock(MediumRepo.class);
        mockBioStateRepo = mock(BioStateRepo.class);
        mockTissueRepo = mock(TissueRepo.class);
        mockExternalBarcodeValidation = mock(Validator.class);
        mockDonorNameValidation = mock(Validator.class);
        mockExternalNameValidation = mock(Validator.class);
        mockVisiumLpBarcodeValidation = mock(Validator.class);
    }

    private SectionRegisterValidation makeValidation(Object requestObj) {
        SectionRegisterRequest request;
        if (requestObj instanceof SectionRegisterRequest) {
            request = (SectionRegisterRequest) requestObj;
        } else {
            Collection<?> col = objToCollection(requestObj);
            if (col.isEmpty()) {
                request = new SectionRegisterRequest(List.of());
            } else {
                Object element = col.iterator().next();
                if (element instanceof SectionRegisterLabware) {
                    request = new SectionRegisterRequest(objToCollection(requestObj));
                } else if (element instanceof SectionRegisterContent) {
                    SectionRegisterLabware srl = new SectionRegisterLabware("X1", "lwtype", objToCollection(requestObj));
                    request = new SectionRegisterRequest(List.of(srl));
                } else {
                    throw new IllegalArgumentException("Couldn't make "+requestObj+" into a request.");
                }
            }
        }
        return spy(new SectionRegisterValidation(request, mockDonorRepo, mockSpeciesRepo, mockLwTypeRepo, mockLwRepo,
                mockMouldSizeRepo, mockHmdmcRepo, mockTissueTypeRepo, mockFixativeRepo, mockMediumRepo,
                mockTissueRepo, mockBioStateRepo,
                mockExternalBarcodeValidation, mockDonorNameValidation, mockExternalNameValidation, mockVisiumLpBarcodeValidation));
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

        var validation = makeValidation(new SectionRegisterRequest());
        final String problem = "Things are bad.";
        if (valid) {
            doNothing().when(validation).checkEmpty();
        } else {
            doAnswer(invocation -> validation.getProblems().add(problem))
                    .when(validation).checkEmpty();
        }
        doReturn(donors).when(validation).validateDonors();
        doReturn(lwTypes).when(validation).validateLabwareTypes();
        doNothing().when(validation).validateBarcodes();
        doReturn(tissues).when(validation).validateTissues(any());
        doReturn(samples).when(validation).validateSamples(any());

        ValidatedSections vs = validation.validate();

        if (valid) {
            assertNotNull(vs);
            assertSame(vs.getDonorMap(), donors);
            assertSame(vs.getLabwareTypes(), lwTypes);
            assertSame(vs.getSampleMap(), samples);
            assertThat(validation.getProblems()).isEmpty();
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
        return Stream.of(
                Arguments.of(new SectionRegisterRequest(List.of()), "No labware specified in request."),
                Arguments.of(new SectionRegisterRequest(List.of(new SectionRegisterLabware())),
                        "Labware requested without contents."),
                Arguments.of(new SectionRegisterRequest(List.of(new SectionRegisterLabware(
                        "X1", "Thing", List.of(new SectionRegisterContent())
                ))), null)
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
                        List.of(new SectionRegisterContent("DONOR1", LifeStage.adult, "Human"),
                                new SectionRegisterContent("DONOR1", LifeStage.adult, "human"),
                                new SectionRegisterContent("DONOR2", LifeStage.fetal, "Human"),
                                new SectionRegisterContent("DONOR2", LifeStage.fetal, "HUMAN"),
                                new SectionRegisterContent("DONOR3", LifeStage.paediatric, "Hamster")),
                        null, List.of(new Donor(null, "DONOR2", LifeStage.fetal, human),
                                new Donor(null, "DONOR3", LifeStage.paediatric, hamster),
                                donor1), knownDonors, knownSpecies
                ),

                // Various individual problems
                Arguments.of(
                        new SectionRegisterContent(null, LifeStage.adult, "Human"),
                        "Missing donor identifier.", null, knownDonors, knownSpecies
                ),
                Arguments.of(
                        new SectionRegisterContent("", LifeStage.adult, "Human"),
                        "Missing donor identifier.", null, knownDonors, knownSpecies
                ),
                Arguments.of(
                        new SectionRegisterContent("DONOR1", null, "Human"),
                        "Missing life stage.", donor1, knownDonors, knownSpecies
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
                            new SectionRegisterContent("DONOR1", LifeStage.adult, "Human"),
                            new SectionRegisterContent("DONOR2", LifeStage.adult, "Human"),
                            new SectionRegisterContent("Donor2", LifeStage.fetal, "Human")
                        ), "Multiple different life stages specified for donor DONOR2",
                        List.of(donor1, new Donor(null, "DONOR2", LifeStage.adult, human)),
                        knownDonors, knownSpecies
                ),
                Arguments.of(
                        List.of(
                                new SectionRegisterContent("DONOR2", LifeStage.adult, "Human"),
                                new SectionRegisterContent("Donor2", LifeStage.adult, "hamster")
                        ), "Multiple different species specified for donor DONOR2",
                        new Donor(null, "DONOR2", LifeStage.adult, human),
                        knownDonors, knownSpecies
                ),
                Arguments.of(
                        new SectionRegisterContent("Donor1", LifeStage.paediatric, "Human"),
                        "Wrong life stage given for existing donor DONOR1", donor1, knownDonors, knownSpecies
                ),
                Arguments.of(
                        new SectionRegisterContent("Donor1", LifeStage.adult, "Hamster"),
                        "Wrong species given for existing donor DONOR1", donor1, knownDonors, knownSpecies
                ),
                Arguments.of(
                        List.of(new SectionRegisterContent("DONOR2", null, ""),
                                new SectionRegisterContent("Donor2", LifeStage.adult, null),
                                new SectionRegisterContent("donor2", null, "Human")),
                        List.of("Missing life stage.", "Missing species."),
                        new Donor(null, "DONOR2", LifeStage.adult, human),
                        knownDonors, knownSpecies
                ),

                // many problems
                Arguments.of(
                        List.of(new SectionRegisterContent("DONOR1", LifeStage.paediatric, "hamster"),
                                new SectionRegisterContent("DONOR2", null, null),
                                new SectionRegisterContent(null, LifeStage.fetal, "Human"),
                                new SectionRegisterContent("Donor2", LifeStage.adult, "Human"),
                                new SectionRegisterContent("DONOR2", LifeStage.fetal, "Hamster"),
                                new SectionRegisterContent("DONOR3", LifeStage.fetal, "Unicorn")),
                        List.of("Wrong life stage given for existing donor DONOR1",
                                "Wrong species given for existing donor DONOR1",
                                "Missing life stage.", "Missing species.", "Missing donor identifier.",
                                "Multiple different species specified for donor DONOR2",
                                "Multiple different life stages specified for donor DONOR2",
                                "Unknown species: \"Unicorn\""
                        ),
                        List.of(donor1, new Donor(null, "DONOR2", LifeStage.adult, human),
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
    public void testValidateBarcodes(Object barcodesObj, String labwareType, Object expectedProblemsObj, Object existingExternalBarcodesObj,
                                     Object existingLabwareBarcodes) {
        Collection<String> barcodes = objToCollection(barcodesObj);
        Collection<String> expectedProblems = objToCollection(expectedProblemsObj);
        SectionRegisterRequest request = new SectionRegisterRequest(barcodes.stream()
                .map(bc -> new SectionRegisterLabware(bc, labwareType, null))
                .collect(toList()));

        if (existingExternalBarcodesObj!=null) {
            Collection<String> xbcs = objToCollection(existingExternalBarcodesObj);
            for (String xbc : xbcs) {
                when(mockLwRepo.existsByExternalBarcode(xbc)).thenReturn(true);
            }
        }
        if (existingLabwareBarcodes!=null) {
            Collection<String> bcs = objToCollection(existingLabwareBarcodes);
            for (String bc : bcs) {
                when(mockLwRepo.existsByBarcode(bc)).thenReturn(true);
            }
        }
        mockValidator(mockExternalBarcodeValidation);
        mockValidator(mockVisiumLpBarcodeValidation);

        SectionRegisterValidation validation = makeValidation(request);
        validation.validateBarcodes();
        assertThat(validation.getProblems()).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> validateBarcodesArgs() {
        return Stream.of(
                Arguments.of("Alpha", "lt", null, null, null),
                Arguments.of(Collections.singletonList(null), "lt", "Missing external barcode.", null, null),
                Arguments.of(List.of("", "X123"), "lt", "Missing external barcode.", null, null),
                Arguments.of(List.of("X11", "!ABC"), "lt", "Bad external barcode: !ABC", null, null),
                Arguments.of(List.of("X11", "!ABC"), "Visium LP", "Bad visium barcode: !ABC", null, null),
                Arguments.of(List.of("Alpha", "Beta", "ALPHA", "BETA", "Gamma"), "lt", "Repeated barcodes: [ALPHA, BETA]", null, null),
                Arguments.of(List.of("X11", "X12", "X13"), "lt", "External barcodes already used: [X11, X12]",
                        List.of("X11", "X12"), null),
                Arguments.of(List.of("X11", "X12", "X13"), "lt", "Labware barcodes already used: [X12, X13]",
                        null, List.of("X12", "X13")),
                Arguments.of(List.of("stan-ABC", "STO-123"), "lt", "Invalid external barcode prefix: [stan-ABC, STO-123]",
                        null, null),
                Arguments.of(List.of("", "!ABC", "Alpha", "ALPHA", "X11", "Y11", "STO-123"), "lt",
                        List.of("Missing external barcode.", "Bad external barcode: !ABC",
                                "Repeated barcode: [ALPHA]", "External barcode already used: [X11]",
                                "Labware barcode already used: [Y11]",
                                "Invalid external barcode prefix: [STO-123]"),
                        "X11", "Y11")
        );
    }

    @ParameterizedTest
    @MethodSource("validateTissuesArgs")
    public void testValidateTissues(ValidateTissueTestData testData) {
        when(mockMouldSizeRepo.findByName("None")).thenReturn(Optional.ofNullable(testData.mouldSize));
        when(mockHmdmcRepo.findAllByHmdmcIn(any())).then(findAllAnswer(testData.hmdmcs, Hmdmc::getHmdmc));
        when(mockTissueTypeRepo.findAllByNameIn(any())).then(findAllAnswer(testData.tissueTypes, TissueType::getName));
        when(mockFixativeRepo.findAllByNameIn(any())).then(findAllAnswer(testData.fixatives, Fixative::getName));
        when(mockMediumRepo.findAllByNameIn(any())).then(findAllAnswer(testData.mediums, Medium::getName));
        when(mockTissueRepo.findAllByExternalNameIn(any())).then(findAllAnswer(testData.existingTissues, Tissue::getExternalName));
        mockValidator(mockExternalNameValidation);

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
        List<Hmdmc> hmdmcs = List.of(hmdmc1, hmdmc2, hmdmc3, hmdmc4);
        final TissueType ARM = makeTissueType(1, "Arm", "ARM");
        final TissueType LEG = makeTissueType(2, "Leg", "LEG");
        List<TissueType> tissueTypes = List.of(ARM, LEG);
        final Fixative fixNone = new Fixative(10, "None");
        final Fixative fix = new Fixative(11, "Formalin");
        List<Fixative> fixatives = List.of(fixNone, fix);
        final Medium mediumNone = new Medium(20, "None");
        final Medium medium = new Medium(21, "Butter");
        List<Medium> mediums = List.of(mediumNone, medium);
        final Donor DONOR1 = new Donor(1, "DONOR1", LifeStage.adult, EntityFactory.getHuman());
        final Donor DONOR2 = new Donor(null, "DONOR2", LifeStage.fetal, null);
        List<Donor> donors = List.of(DONOR1, DONOR2);
        MouldSize mouldSize = new MouldSize(1, "None");
        Supplier<ValidateTissueTestData> testData = () ->
                new ValidateTissueTestData(hmdmcs, tissueTypes, fixatives, mediums, donors, mouldSize);

        return Stream.of(
                // Good request
                testData.get()
                        .content("EXT1", 4, "Arm", 1, "Donor1", "None", "None", "2021/01", "human")
                        .content("EXT2", 5, "Leg", 2, "Donor1", "butter", "Formalin", "2021/02", "human")
                        .content("EXT3", 5, "Leg", 1, "Donor2", "butter", "Formalin", null, "hamster")
                        .tissues(new Tissue(null, "EXT1", 4, ARM.getSpatialLocations().get(0), DONOR1, mouldSize, mediumNone, fixNone, hmdmc1),
                                new Tissue(null, "EXT2", 5, LEG.getSpatialLocations().get(1), DONOR1, mouldSize, medium, fix, hmdmc2),
                                new Tissue(null, "EXT3", 5, LEG.getSpatialLocations().get(0), DONOR2, mouldSize, medium, fix, null)),

                // Single problems
                testData.get()
                        .content("EXT1", 4, "ARM", 1, null, "None", "None", "2021/01", "human")
                        .noMouldSize()
                        .problem("Mould size \"None\" not found."),
                testData.get()
                        .content("EXT1", 4, "ARM", 1, "Donor1", "None", "None", null, "human")
                        .problem("Missing HMDMC number."),
                testData.get()
                        .content("EXT1", 4, "ARM", 1, "Donor1", "None", "None", "", "human")
                        .content("EXT2", 4, "ARM", 1, "Donor1", "None", "None", "2021/01", "")
                        .problem("Missing HMDMC number."),
                testData.get()
                        .content("EXT1", 4, "ARM", 1, "Donor1", "None", "None", "2021/404", "human")
                        .content("EXT2", 4, "ARM", 1, "Donor1", "None", "None", "2021/405", "human")
                        .content("EXT3", 4, "ARM", 1, "Donor1", "None", "None", "2021/405", "human")
                        .problem("Unknown HMDMC numbers: [2021/404, 2021/405]"),
                testData.get()
                        .content("EXT1", 4, "ARM", 1, "Donor2", "None", "None", "2021/01", "Hamster")
                        .problem("Unexpected HMDMC number received for non-human tissue."),
                testData.get()
                        .content("EXT1", 4, "ARM", 1, "Donor2", "None", "None", "2021/03", "Human")
                        .content("EXT2", 4, "ARM", 1, "Donor2", "None", "None", "2021/04", "Human")
                        .problem("HMDMC not enabled: [2021/03, 2021/04]"),
                testData.get()
                        .content(null, 4, "ARM", 1, "Donor1", "None", "None", "2021/01", "Human")
                        .problem("Missing external identifier."),
                testData.get()
                        .content("", 4, "ARM", 1, "Donor1", "None", "None", "2021/01", "Human")
                        .problem("Missing external identifier."),
                testData.get()
                        .content("!DN", 4, "ARM", 1, "Donor1", "none", "none", "2021/01", "human")
                        .problem("Bad external name: !DN"),
                testData.get()
                        .content("EXT1", 4, "ARM", 1, "Donor1", "None", "None", "2021/01", "Human")
                        .content("EXT1", 5, "ARM", 1, "Donor1", "None", "None", "2021/01", "Human")
                        .content("EXT2", 4, "ARM", 1, "Donor1", "None", "None", "2021/01", "Human")
                        .content("EXT2", 5, "ARM", 1, "Donor1", "None", "None", "2021/01", "Human")
                        .content("EXT3", 5, "ARM", 1, "Donor1", "None", "None", "2021/01", "Human")
                        .problem("Repeated external identifiers: [EXT1, EXT2]"),
                testData.get()
                        .content("TISSUE1", 4, "ARM", 1, "Donor1", "none", "none", "2021/01", "human")
                        .content("TISSUE2", 4, "ARM", 1, "Donor1", "none", "none", "2021/01", "human")
                        .content("TISSUE3", 4, "ARM", 1, "Donor1", "none", "none", "2021/01", "human")
                        .existing(new Tissue(1, "TISSUE1", 3, ARM.getSpatialLocations().get(0), DONOR1, mouldSize, medium, fix, hmdmcs.get(0)),
                                new Tissue(2, "TISSUE2", 3, ARM.getSpatialLocations().get(0), DONOR1, mouldSize, medium, fix, hmdmcs.get(0)))
                        .problem("External identifiers already in use: [TISSUE1, TISSUE2]"),
                testData.get()
                        .content("EXT1", 4, null, 1, "Donor1", "None", "None", "2021/01", "Human")
                        .problem("Missing tissue type."),
                testData.get()
                        .content("EXT1", 4, "Squirrel", 1, "Donor1", "None", "None", "2021/01", "Human")
                        .problem("Unknown tissue type: [Squirrel]"),
                testData.get()
                        .content("EXT1", 4, "ARM", null, "Donor1", "None", "None", "2021/01", "Human")
                        .problem("Missing spatial location."),
                testData.get()
                        .content("EXT1", 4, "ARM", 5, "Donor1", "None", "None", "2021/01", "Human")
                        .content("EXT2", 4, "LEG", 3, "Donor1", "None", "None", "2021/01", "Human")
                        .problem("Unknown spatial locations: [5 for Arm, 3 for Leg]"),
                testData.get()
                        .content("EXT1", null, "ARM", 1, "Donor1", "None", "None", "2021/01", "Human")
                        .problem("Missing replicate number."),
                testData.get()
                        .content("EXT1", -4, "ARM", 1, "Donor1", "None", "None", "2021/01", "Human")
                        .problem("Replicate number cannot be negative."),
                testData.get()
                        .content("EXT1", 4, "ARM", 1, "Donor1", "Custard", "None", "2021/01", "Human")
                        .content("EXT2", 4, "ARM", 1, "Donor1", "Jelly", "None", "2021/01", "Human")
                        .problem("Unknown mediums: [Custard, Jelly]"),
                testData.get()
                        .content("EXT1", 4, "ARM", 1, "Donor1", "None", "Glue", "2021/01", "Human")
                        .content("EXT2", 4, "ARM", 1, "Donor1", "None", "Stapler", "2021/01", "Human")
                        .problem("Unknown fixatives: [Glue, Stapler]"),


                // Mixed problems
                testData.get()
                        .content(null, null, null, null, null, null, null, null, "Human")
                        .problems("Missing external identifier.", "Missing replicate number.", "Missing tissue type.", "Missing spatial location.", "Missing medium.",
                                "Missing fixative.", "Missing HMDMC number."),
                testData.get()
                        .content("!X11", -1, "Squirrel", 2, null, "Custard", "Stapler", "2021/404", null)
                        .problems("Bad external name: !X11", "Replicate number cannot be negative.", "Unknown tissue type: [Squirrel]", "Unknown medium: [Custard]",
                                "Unknown fixative: [Stapler]", "Unknown HMDMC number: [2021/404]")

        );
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
                Arguments.of(content("TISSUE1", 4, 0), tissues,
                        "Section thickness cannot be zero.", null, bs),
                Arguments.of(content("TISSUE1", 4, -3), tissues,
                        "Section thickness cannot be negative.", null, bs),
                Arguments.of(content("TISSUE1", 2, 4), tissues,
                        "Bio state \"Tissue\" not found.", null, null),

                Arguments.of(content("TISSUE1", null, 0), tissues,
                        List.of("Missing section number.", "Section thickness cannot be zero.",
                                "Bio state \"Tissue\" not found."), null, null)
        );
    }

    private static SectionRegisterContent content(String extName, Integer section, Integer thickness) {
        SectionRegisterContent content = new SectionRegisterContent();
        content.setExternalIdentifier(extName);
        content.setSectionNumber(section);
        content.setSectionThickness(thickness);
        return content;
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
        List<Tissue> existingTissues;
        List<Donor> donors;
        MouldSize mouldSize;

        public ValidateTissueTestData(List<Hmdmc> hmdmcs, List<TissueType> tissueTypes, List<Fixative> fixatives,
                                      List<Medium> mediums, List<Donor> donors, MouldSize mouldSize) {
            this.expectedProblems = List.of();
            this.expectedTissues = List.of();
            this.contents = new ArrayList<>();
            this.hmdmcs = hmdmcs;
            this.tissueTypes = tissueTypes;
            this.fixatives = fixatives;
            this.mediums = mediums;
            this.existingTissues = List.of();
            this.donors = donors;
            this.mouldSize = mouldSize;
        }

        public ValidateTissueTestData problem(String problem) {
            this.expectedProblems = List.of(problem);
            return this;
        }
        public ValidateTissueTestData problems(String... problems) {
            this.expectedProblems = List.of(problems);
            return this;
        }

        public ValidateTissueTestData noMouldSize() {
            this.mouldSize = null;
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

        public ValidateTissueTestData content(String externalName, Integer replicate, String tissueType, Integer spatLoc, String donorName, String medium, String fixative, String hmdmc, String species) {
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
            return new SectionRegisterRequest(List.of(srl));
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
        return new SectionRegisterRequest(srls);
    }

    @SuppressWarnings("unchecked")
    private <V> UCMap<V> objToUCMap(Object obj, Function<V, String> keyFunction) {
        if (obj==null) {
            return new UCMap<>();
        }
        if (obj instanceof UCMap) {
            return (UCMap<V>) obj;
        }
        if (obj instanceof Map) {
            return new UCMap<>((Map<String, V>) obj);
        }
        if (obj instanceof Collection) {
            return ((Collection<V>) obj).stream().collect(UCMap.toUCMap(keyFunction));
        }
        return UCMap.from(keyFunction, (V) obj);
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
