package uk.ac.sanger.sccp.stan.service.register;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.register.*;
import uk.ac.sanger.sccp.stan.service.BioRiskService;
import uk.ac.sanger.sccp.stan.service.Validator;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;
import uk.ac.sanger.sccp.utils.Zip;

import java.time.LocalDate;
import java.util.*;
import java.util.function.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

class TestBlockRegisterValidation {
    @Mock
    DonorRepo mockDonorRepo;
    @Mock
    HmdmcRepo mockHmdmcRepo;
    @Mock
    TissueTypeRepo mockTtRepo;
    @Mock
    LabwareTypeRepo mockLtRepo;
    @Mock
    MediumRepo mockMediumRepo;
    @Mock
    FixativeRepo mockFixativeRepo;
    @Mock
    TissueRepo mockTissueRepo;
    @Mock
    SpeciesRepo mockSpeciesRepo;
    @Mock
    CellClassRepo mockCellClassRepo;
    @Mock
    LabwareRepo mockLwRepo;
    @Mock
    Validator<String> mockDonorNameValidator;
    @Mock
    Validator<String> mockExternalNameValidator;
    @Mock
    Validator<String> mockReplicateValidator;
    @Mock
    Validator<String> mockExternalBarcodeValidator;
    @Mock
    BlockFieldChecker mockBlockFieldChecker;
    @Mock
    BioRiskService mockBioRiskService;
    @Mock
    WorkService mockWorkService;

    private AutoCloseable mocking;
    
    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
    }

    static BlockRegisterRequest toRequest(List<BlockRegisterSample> brss) {
        BlockRegisterLabware brl = new BlockRegisterLabware();
        brl.setSamples(brss);
        BlockRegisterRequest request = new BlockRegisterRequest();
        request.setLabware(List.of(brl));
        return request;
    }

    BlockRegisterValidationImp makeVal(BlockRegisterRequest request) {
        return spy(new BlockRegisterValidationImp(request, mockDonorRepo, mockHmdmcRepo, mockTtRepo, mockLtRepo,
                mockMediumRepo, mockFixativeRepo, mockTissueRepo, mockSpeciesRepo, mockCellClassRepo, mockLwRepo,
                mockDonorNameValidator, mockExternalNameValidator, mockReplicateValidator, mockExternalBarcodeValidator,
                mockBlockFieldChecker, mockBioRiskService, mockWorkService));
    }

    private static List<Consumer<BlockRegisterValidationImp>> valMethods() {
        return List.of(BlockRegisterValidationImp::validateDonors,
                BlockRegisterValidationImp::validateHmdmcs,
                BlockRegisterValidationImp::validateSpatialLocations,
                BlockRegisterValidationImp::validateLabwareTypes,
                BlockRegisterValidationImp::validateExternalBarcodes,
                BlockRegisterValidationImp::validateAddresses,
                BlockRegisterValidationImp::validateMediums,
                BlockRegisterValidationImp::validateFixatives,
                BlockRegisterValidationImp::validateCollectionDates,
                BlockRegisterValidationImp::validateExistingTissues,
                BlockRegisterValidationImp::validateNewTissues,
                BlockRegisterValidationImp::validateBioRisks,
                BlockRegisterValidationImp::validateWorks,
                BlockRegisterValidationImp::validateCellClasses
        );
    }

    @Test
    void testValidate_empty() {
        BlockRegisterRequest request = new BlockRegisterRequest();
        BlockRegisterValidationImp val = makeVal(request);
        var problems = val.validate();
        for (var method : valMethods()) {
            method.accept(verify(val, never()));
        }
        assertThat(problems).containsExactly("No labware specified in request.");
    }

    @Test
    void testValidate() {
        BlockRegisterRequest request = new BlockRegisterRequest();
        request.setLabware(List.of(new BlockRegisterLabware()));
        request.getLabware().getFirst().setSamples(List.of(new BlockRegisterSample()));
        BlockRegisterValidationImp val = makeVal(request);
        for (var method : valMethods()) {
            method.accept(doNothing().when(val));
        }
        assertThat(val.validate()).isEmpty();
        InOrder inOrder = inOrder(val);
        for (var method : valMethods()) {
            method.accept(inOrder.verify(val));
        }
    }

    @Test
    void testValidateDonors_problems() {
        List<BlockRegisterSample> brss = List.of(
                brsForDonor(null, "Human", LifeStage.adult),
                brsForDonor("Donor!", "Human", LifeStage.adult),
                brsForDonor("DONOR1", null, LifeStage.adult),
                brsForDonor("DONOR2", "Humming", LifeStage.adult),
                brsForDonor("DONOR3", "Mutant", LifeStage.adult),
                brsForDonor("DONOR4", "Human", LifeStage.adult),
                brsForDonor("Donor4", "Hamster", LifeStage.adult),
                brsForDonor("DONOR5", "Human", LifeStage.adult),
                brsForDonor("DONOR5", "Human", LifeStage.fetal),
                brsForDonor("DONORA", "Hamster", LifeStage.adult),
                brsForDonor("DONORB", "Human", LifeStage.fetal)
        );
        BlockRegisterRequest request = toRequest(brss);

        Species human = new Species(1, "Human");
        Species hamster = new Species(2, "Hamster");
        Species mutant = new Species(3, "Mutant");
        mutant.setEnabled(false);
        when(mockSpeciesRepo.findByName(anyString())).thenReturn(Optional.empty());
        Stream.of(human, hamster, mutant).forEach(s -> doReturn(Optional.of(s)).when(mockSpeciesRepo).findByName(eqCi(s.getName())));
        Donor donora = new Donor(10, "DONORA", LifeStage.adult, human);
        Donor donorb = new Donor(11, "DONORB", LifeStage.adult, human);
        when(mockDonorRepo.findByDonorName(anyString())).thenReturn(Optional.empty());
        Stream.of(donora, donorb).forEach(d -> doReturn(Optional.of(d)).when(mockDonorRepo).findByDonorName(eqCi(d.getDonorName())));
        mockStringValidator(mockDonorNameValidator, "donor name");
        BlockRegisterValidationImp val = makeVal(request);
        val.validateDonors();
        var problems = val.getProblems();
        assertThat(problems).containsExactlyInAnyOrder(
                "Missing donor identifier.",
                "Bad donor name: Donor!",
                "Missing species.",
                "Unknown species: \"Humming\"",
                "Species is not enabled: Mutant",
                "Multiple different species specified for donor DONOR4",
                "Multiple different life stages specified for donor DONOR5",
                "Wrong species given for existing donor DONORA",
                "Wrong life stage given for existing donor DONORB"
        );
        assertSame(donora, val.getDonor("donorA"));
        assertSame(donorb, val.getDonor("donorb"));
        assertNotNull(val.getDonor("donor4"));
    }

    @Test
    void testValidateDonors_ok() {
        List<BlockRegisterSample> brss = List.of(
                brsForDonor("donor1", "Human", LifeStage.adult),
                brsForDonor("DONOR1", "Human", LifeStage.adult),
                brsForDonor("donora", "Human", LifeStage.adult)
        );
        Species human = new Species(1, "Human");
        when(mockSpeciesRepo.findByName(eqCi(human.getName()))).thenReturn(Optional.of(human));
        Donor donora = new Donor(1, "DONORA", LifeStage.adult, human);
        when(mockDonorRepo.findByDonorName(anyString())).thenReturn(Optional.empty());
        doReturn(Optional.of(donora)).when(mockDonorRepo).findByDonorName(eqCi(donora.getDonorName()));
        BlockRegisterRequest request = toRequest(brss);
        BlockRegisterValidationImp val = makeVal(request);
        val.validateDonors();
        assertSame(donora, val.getDonor("DonorA"));
        assertNotNull(val.getDonor("Donor1"));
        assertThat(val.getProblems()).isEmpty();
    }

    private static BlockRegisterSample brsForDonor(String donorName, String species, LifeStage lifeStage) {
        BlockRegisterSample brs = new BlockRegisterSample();
        brs.setDonorIdentifier(donorName);
        brs.setSpecies(species);
        brs.setLifeStage(lifeStage);
        return brs;
    }

    @Test
    void testValidateHmdmcs_problems() {
        final String HUMAN_NAME = Species.HUMAN_NAME;
        Hmdmc hmdmc0 = new Hmdmc(10, "20/000");
        Hmdmc hmdmcX = new Hmdmc(11, "20/001");
        hmdmcX.setEnabled(false);
        when(mockHmdmcRepo.findByHmdmc(anyString())).thenReturn(Optional.empty());
        Stream.of(hmdmc0, hmdmcX).forEach(h -> doReturn(Optional.of(h)).when(mockHmdmcRepo).findByHmdmc(h.getHmdmc()));

        List<BlockRegisterSample> brss = List.of(
                brsForHmdmc(null, HUMAN_NAME),
                brsForHmdmc("20/000", HUMAN_NAME),
                brsForHmdmc("20/000", "Hamster"),
                brsForHmdmc("20/001", HUMAN_NAME),
                brsForHmdmc("20/002", HUMAN_NAME)
        );
        BlockRegisterRequest request = toRequest(brss);
        BlockRegisterValidationImp val = makeVal(request);
        val.validateHmdmcs();
        assertThat(val.getProblems()).containsExactlyInAnyOrder(
                "Missing HuMFre number.", "Non-human tissue should not have a HuMFre number.",
                "Unknown HuMFre number: [20/002]", "HuMFre number not enabled: [20/001]"
        );
        assertSame(hmdmc0, val.getHmdmc("20/000"));
        assertSame(hmdmcX, val.getHmdmc("20/001"));
    }

    @Test
    void testValidateHmdmcs_ok() {
        final String HUMAN_NAME = Species.HUMAN_NAME;
        Hmdmc hmdmc0 = new Hmdmc(10, "20/000");
        when(mockHmdmcRepo.findByHmdmc(hmdmc0.getHmdmc())).thenReturn(Optional.of(hmdmc0));

        List<BlockRegisterSample> brss = List.of(
                brsForHmdmc("20/000", HUMAN_NAME),
                brsForHmdmc(null, "Hamster")
        );
        BlockRegisterRequest request = toRequest(brss);
        BlockRegisterValidationImp val = makeVal(request);
        val.validateHmdmcs();
        assertThat(val.getProblems()).isEmpty();
        assertSame(hmdmc0, val.getHmdmc("20/000"));
    }

    private static BlockRegisterSample brsForHmdmc(String hmdmc, String speciesName) {
        BlockRegisterSample brs = new BlockRegisterSample();
        brs.setHmdmc(hmdmc);
        brs.setSpecies(speciesName);
        return brs;
    }

    @Test
    void testValidateSpatialLocations_problems() {
        List<BlockRegisterSample> brss = List.of(
                brsForSL(null, 0), // missing tissue type
                brsForSL("Legg", 0), // unknown tissue type
                brsForSL("Tail", 0), // disabled tissue type
                brsForSL("Leg", 13), // unknown spatial location
                brsForSL("Leg", 1), // disabled spatial location
                brsForSL("LEG", 0) // ok
        );
        TissueType tail = new TissueType(1, "Tail", "Tail");
        tail.setSpatialLocations(List.of(new SpatialLocation(0, "Alpha", 0, tail)));
        tail.setEnabled(false);
        TissueType leg = new TissueType(2, "Leg", "Leg");
        leg.setSpatialLocations(List.of(new SpatialLocation(20, "Alpha", 0, leg),
                new SpatialLocation(21, "Beta", 1, leg)));
        leg.getSpatialLocations().getLast().setEnabled(false);
        when(mockTtRepo.findByName(anyString())).thenReturn(Optional.empty());
        Stream.of(tail, leg).forEach(tt -> doReturn(Optional.of(tt)).when(mockTtRepo).findByName(eqCi(tt.getName())));
        BlockRegisterRequest request = toRequest(brss);
        BlockRegisterValidationImp val = makeVal(request);
        val.validateSpatialLocations();
        assertThat(val.getProblems()).containsExactlyInAnyOrder(
                "Missing tissue type.",
                "Unknown tissue type: [Legg]",
                "Tissue type \"Tail\" is disabled.",
                "Unknown spatial location 13 for tissue type Leg.",
                "Spatial location is disabled: 1 for tissue type Leg."
        );
        assertSame(leg.getSpatialLocations().getFirst(), val.getSpatialLocation("LEG", 0));
    }

    @Test
    void testValidateSpatialLocations_ok() {
        List<BlockRegisterSample> brss = List.of(brsForSL("Leg", 0));
        TissueType leg = new TissueType(1, "Leg", "Leg");
        leg.setSpatialLocations(List.of(new SpatialLocation(10, "Alpha", 0, leg)));
        when(mockTtRepo.findByName(eqCi(leg.getName()))).thenReturn(Optional.of(leg));
        BlockRegisterRequest request = toRequest(brss);
        BlockRegisterValidationImp val = makeVal(request);
        val.validateSpatialLocations();
        assertThat(val.getProblems()).isEmpty();
        assertSame(leg.getSpatialLocations().getFirst(), val.getSpatialLocation("LEG", 0));
    }

    private static BlockRegisterSample brsForSL(String ttName, int slCode) {
        BlockRegisterSample brs = new BlockRegisterSample();
        brs.setTissueType(ttName);
        brs.setSpatialLocation(slCode);
        return brs;
    }

    @Test
    void testValidateLabwareTypes() {
        testGeneric(EntityFactory.getTubeType(), LabwareType::getName, "labware type",
                mockLtRepo, BlockRegisterLabware::setLabwareType, LabwareTypeRepo::findByName,
                BlockRegisterValidationImp::validateLabwareTypes,
                BlockRegisterValidationImp::getLabwareType);
    }

    @Test
    void testValidateMediums() {
        testGeneric(EntityFactory.getMedium(),  Medium::getName, "medium",
                mockMediumRepo, BlockRegisterLabware::setMedium, MediumRepo::findByName,
                BlockRegisterValidationImp::validateMediums,
                BlockRegisterValidationImp::getMedium);
    }

    @Test
    void testValidateFixatives() {
        testGeneric(EntityFactory.getFixative(), Fixative::getName, "fixative",
                mockFixativeRepo, BlockRegisterLabware::setFixative, FixativeRepo::findByName,
                BlockRegisterValidationImp::validateFixatives,
                BlockRegisterValidationImp::getFixative);
    }

    <R, E> void testGeneric(E validEntity, Function<E,String> entityNameGetter, String entityTypeName,
                            R repo,
                            BiConsumer<BlockRegisterLabware, String> nameSetter,
                            BiFunction<R, String, Optional<E>> lkp,
                            Consumer<BlockRegisterValidationImp> valMethod,
                            BiFunction<BlockRegisterValidationImp, String, E> retriever) {
        String entityName = entityNameGetter.apply(validEntity);
        lkp.apply(doReturn(Optional.empty()).when(repo), any());
        lkp.apply(doReturn(Optional.of(validEntity)).when(repo), eqCi(entityName));

        BlockRegisterLabware brl = new BlockRegisterLabware();
        nameSetter.accept(brl, entityName);
        BlockRegisterRequest request = new BlockRegisterRequest();
        request.setLabware(List.of(brl));
        BlockRegisterValidationImp val = makeVal(request);
        valMethod.accept(val);
        assertThat(val.getProblems()).isEmpty();
        assertSame(validEntity, retriever.apply(val, entityName));

        brl = new BlockRegisterLabware();
        nameSetter.accept(brl, "invalid");
        request = new BlockRegisterRequest();
        request.setLabware(List.of(brl));
        val = makeVal(request);
        valMethod.accept(val);
        assertThat(val.getProblems()).containsExactly("Unknown " + entityTypeName + ": [\"invalid\"]");

        brl = new BlockRegisterLabware();
        nameSetter.accept(brl, null);
        request = new BlockRegisterRequest();
        request.setLabware(List.of(brl));
        val = makeVal(request);
        valMethod.accept(val);
        assertThat(val.getProblems()).containsExactly("Missing " + entityTypeName + ".");
    }


    @Test
    void testValidateCollectionDates_problems() {
        String HUMAN_NAME = Species.HUMAN_NAME;
        LocalDate today = LocalDate.now();
        LocalDate invalidDate = today.plusDays(2L);
        List<BlockRegisterSample> brss = List.of(
                brsForDate("EXT1", HUMAN_NAME, LifeStage.fetal, null), // date missing
                brsForDate("ext2", HUMAN_NAME, LifeStage.fetal, invalidDate), // bad date
                brsForDate("ext3", HUMAN_NAME, LifeStage.fetal, today.minusDays(2L)),
                brsForDate("Ext3", HUMAN_NAME, LifeStage.fetal, today.minusDays(3L))
        );
        BlockRegisterRequest request = toRequest(brss);
        BlockRegisterValidationImp val = makeVal(request);
        val.validateCollectionDates();
        assertThat(val.getProblems()).containsExactlyInAnyOrder(
                "Human fetal samples must have a collection date.",
                "Invalid sample collection date: ["+invalidDate+"]",
                "Inconsistent collection dates specified for tissue EXT3."
        );
    }

    @Test
    void testValidateCollectionDates_ok() {
        String HUMAN_NAME = Species.HUMAN_NAME;
        LocalDate validDate = LocalDate.now().minusDays(2L);
        List<BlockRegisterSample> brss = List.of(
                brsForDate("EXT1", HUMAN_NAME, LifeStage.fetal, validDate),
                brsForDate("Ext1", HUMAN_NAME, LifeStage.fetal, validDate), // matching date
                brsForDate("ext2", HUMAN_NAME, LifeStage.adult, null),
                brsForDate("Ext3", "Hamster", LifeStage.fetal, null)
        );
        BlockRegisterRequest request = toRequest(brss);
        BlockRegisterValidationImp val = makeVal(request);
        val.validateCollectionDates();
        assertThat(val.getProblems()).isEmpty();
    }

    static BlockRegisterSample brsForDate(String externalName, String species, LifeStage ls, LocalDate date) {
        BlockRegisterSample brs = new BlockRegisterSample();
        brs.setExternalIdentifier(externalName);
        brs.setSpecies(species);
        brs.setLifeStage(ls);
        brs.setSampleCollectionDate(date);
        return brs;
    }

    @Test
    void testValidateExistingTissues_problems() {
        BlockRegisterLabware brl1 = new BlockRegisterLabware();
        brl1.setSamples(List.of(
                brsForExistingTissue("Ext1", true),
                brsForExistingTissue("EXT404", true)
        ));
        BlockRegisterLabware brl2 = new BlockRegisterLabware();
        brl2.setSamples(List.of(brsForExistingTissue("ext2", true),
                brsForExistingTissue(null, true),
                brsForExistingTissue("Exta", false)
        ));
        BlockRegisterRequest request = new BlockRegisterRequest();
        request.setLabware(List.of(brl1, brl2));

        Tissue ext1 = tissueWithExtName("EXT1");
        Tissue ext2 = tissueWithExtName("EXT2");
        when(mockTissueRepo.findAllByExternalNameIn(any())).thenReturn(List.of(ext1, ext2));

        doAnswer(invocation -> {
            Consumer<String> problemConsumer = invocation.getArgument(0);
            problemConsumer.accept("Check failed for "+ext2.getExternalName());
            return null;
        }).when(mockBlockFieldChecker).check(any(), any(), any(), same(ext2));

        var val = makeVal(request);
        val.validateExistingTissues();

        verify(mockTissueRepo).findAllByExternalNameIn(Set.of("Ext1", "EXT404", "ext2"));

        verify(mockBlockFieldChecker).check(any(), same(brl1), same(brl1.getSamples().getFirst()), same(ext1));
        verify(mockBlockFieldChecker).check(any(), same(brl2), same(brl2.getSamples().getFirst()), same(ext2));
        verifyNoMoreInteractions(mockBlockFieldChecker);

        assertSame(ext1, val.getTissue("Ext1"));
        assertSame(ext2, val.getTissue("ext2"));
        assertThat(val.getProblems()).containsExactlyInAnyOrder(
                "Missing external identifier.",
                "Existing external identifier not recognised: [\"EXT404\"]",
                "Check failed for EXT2"
        );
    }

    @Test
    void testValidateExistingTissues_ok() {
        BlockRegisterLabware brl = new BlockRegisterLabware();
        brl.setSamples(List.of(brsForExistingTissue("Ext1", true), brsForExistingTissue("ExtA", false)));
        BlockRegisterRequest request = new BlockRegisterRequest();
        request.setLabware(List.of(brl));
        Tissue ext1 = tissueWithExtName("EXT1");
        when(mockTissueRepo.findAllByExternalNameIn(any())).thenReturn(List.of(ext1));
        var val = makeVal(request);
        val.validateExistingTissues();
        assertThat(val.getProblems()).isEmpty();
        assertSame(ext1, val.getTissue("Ext1"));
        verify(mockTissueRepo).findAllByExternalNameIn(Set.of("Ext1"));
        verify(mockBlockFieldChecker).check(any(), same(brl), same(brl.getSamples().getFirst()), same(ext1));
        verifyNoMoreInteractions(mockBlockFieldChecker);
    }

    static BlockRegisterSample brsForExistingTissue(String externalName, boolean existing) {
        BlockRegisterSample brs = new BlockRegisterSample();
        brs.setExternalIdentifier(externalName);
        brs.setExistingTissue(existing);
        return brs;
    }

    static Tissue tissueWithExtName(String externalName) {
        Tissue t = EntityFactory.makeTissue(EntityFactory.getDonor(), EntityFactory.getSpatialLocation());
        t.setExternalName(externalName);
        return t;
    }

    @Test
    void testValidateNewTissues() {
        List<BlockRegisterSample> brss = List.of(
                brsForNewTissue("Ext1", "R1", -1), // negative highest section
                brsForNewTissue("Ext2", "", 1), // missing repl
                brsForNewTissue(null, "R1", 1), // missing external name
                brsForExistingTissue("EXTA", true), // Existing--skip
                brsForNewTissue("EXTB", "R1", 1), // exists unexpectedly
                brsForNewTissue("EXT3", "R1", 1),
                brsForNewTissue("Ext3", "R1", 1), // repeated
                brsForNewTissue("EXT!", "R1", 1), // bad ext name
                brsForNewTissue("EXT4", "R!", 1) // bad repl
        );
        BlockRegisterRequest request = toRequest(brss);
        mockStringValidator(mockExternalNameValidator, "external name");
        mockStringValidator(mockReplicateValidator, "replicate");
        Tissue tis = tissueWithExtName("EXTB");
        when(mockTissueRepo.findAllByExternalName(eqCi("EXTB"))).thenReturn(List.of(tis));

        var val = makeVal(request);
        val.validateNewTissues();
        verify(mockReplicateValidator, times(6)).validate(eq("R1"), any());
        verify(mockReplicateValidator, times(1)).validate(eq("R!"), any());
        verifyNoMoreInteractions(mockReplicateValidator);
        Stream.of("Ext1", "Ext2", "EXTB", "EXT3", "Ext3", "EXT!", "EXT4")
                .forEach(xn -> verify(mockExternalNameValidator).validate(eq(xn), any()));
        verifyNoMoreInteractions(mockExternalNameValidator);

        assertThat(val.getProblems()).containsExactlyInAnyOrder(
                "Highest section number cannot be negative.",
                "Missing replicate number.",
                "Missing external identifier.",
                "Repeated external identifier: Ext3",
                "Bad external name: EXT!",
                "Bad replicate: R!",
                "There is already tissue in the database with external identifier EXTB."
        );
    }

    @Test
    void testValidateNewTissues_ok() {
        List<BlockRegisterSample> brss = List.of(
                brsForNewTissue("Ext1", "R1", 1),
                brsForExistingTissue("EXTA", true)
        );
        BlockRegisterRequest request = toRequest(brss);
        var val = makeVal(request);
        val.validateNewTissues();
        verify(mockReplicateValidator).validate(eq("R1"), any());
        verifyNoMoreInteractions(mockReplicateValidator);
        verify(mockExternalNameValidator).validate(eq("Ext1"), any());
        verifyNoMoreInteractions(mockExternalNameValidator);
        verify(mockTissueRepo).findAllByExternalName(eqCi("Ext1"));
        verifyNoMoreInteractions(mockTissueRepo);
        assertThat(val.getProblems()).isEmpty();
    }

    static BlockRegisterSample brsForNewTissue(String extName, String repl, Integer highestSec) {
        BlockRegisterSample brs = new BlockRegisterSample();
        brs.setExternalIdentifier(extName);
        brs.setReplicateNumber(repl);
        brs.setHighestSection(highestSec);
        return brs;
    }

    @Test
    void testValidateBioRisks() {
        final UCMap<BioRisk> knownBioRisks = new UCMap<>(2);
        Zip.enumerate(Stream.of("BR1", "BR2")).forEach((i,s) -> knownBioRisks.put(s, new BioRisk(i+1, s)));
        when(mockBioRiskService.loadAndValidateBioRisks(any(), any(), any(), any())).then(invocation -> {
            Collection<String> problems = invocation.getArgument(0);
            Stream<BlockRegisterSample> stream = invocation.getArgument(1);
            Function<BlockRegisterSample, String> getter = invocation.getArgument(2);
            BiConsumer<BlockRegisterSample, String> setter = invocation.getArgument(3);
            UCMap<BioRisk> brMap = new UCMap<>();
            stream.forEach(brs -> {
                String bioRiskCode = getter.apply(brs);
                if (nullOrEmpty(bioRiskCode)) {
                    problems.add("Missing bio risk code.");
                    setter.accept(brs, null);
                } else {
                    BioRisk br = knownBioRisks.get(bioRiskCode);
                    if (br == null) {
                        problems.add("Unknown bio risk code: "+bioRiskCode);
                    } else {
                        brMap.put(br.getCode(), br);
                    }
                }
            });
            return brMap;
        });

        List<BlockRegisterSample> brss = Stream.of(null, "", "BR1", "BR2", "BR404")
                .map(TestBlockRegisterValidation::brsForBioRisk).toList();
        BlockRegisterRequest request = toRequest(brss);
        var val = makeVal(request);
        val.validateBioRisks();
        verify(mockBioRiskService).loadAndValidateBioRisks(any(), any(), any(), any());
        assertThat(val.getProblems()).containsExactlyInAnyOrder("Missing bio risk code.", "Unknown bio risk code: BR404");
        assertNull(brss.get(1).getBioRiskCode()); // Empty string has been replaced with null
        assertEquals("BR1", brss.get(2).getBioRiskCode());
        Stream.of("BR1", "BR2").forEach(s -> assertSame(knownBioRisks.get(s), val.getBioRisk(s)));
    }

    static BlockRegisterSample brsForBioRisk(String bioRiskCode) {
        BlockRegisterSample brs = new BlockRegisterSample();
        brs.setBioRiskCode(bioRiskCode);
        return brs;
    }

    @Test
    void testValidateCellClasses() {
        List<BlockRegisterSample> brss = Stream.of(null, "cc1", "cc404")
                .map(TestBlockRegisterValidation::brsForCellClass).toList();
        CellClass cc = new CellClass(1, "CC1", false, true);
        when(mockCellClassRepo.findMapByNameIn(any())).thenReturn(UCMap.from(CellClass::getName, cc));
        BlockRegisterRequest request = toRequest(brss);
        var val = makeVal(request);
        val.validateCellClasses();
        verify(mockCellClassRepo).findMapByNameIn(Set.of("cc1", "cc404"));
        assertThat(val.getProblems()).containsExactlyInAnyOrder(
                "Missing cell class name.",
                "Unknown cell class name: [\"cc404\"]"
        );
        assertSame(cc, val.getCellClass("cc1"));
    }

    static BlockRegisterSample brsForCellClass(String cellClass) {
        BlockRegisterSample brs = new BlockRegisterSample();
        brs.setCellClass(cellClass);
        return brs;
    }

    @ParameterizedTest
    @CsvSource({
            "false,false",
            "true,false",
            "true,true",
    })
    void testValidateWorks(boolean anyWorks, boolean anyInvalid) {
        BlockRegisterRequest request = new BlockRegisterRequest();
        List<Work> works;
        String problem;
        if (anyWorks) {
            request.setWorkNumbers(List.of("SGP1"));
            works = List.of(EntityFactory.makeWork("SGP1"));
            problem = anyInvalid ? "Bad work" : null;
            mayAddProblem(problem, UCMap.from(works, Work::getWorkNumber))
                    .when(mockWorkService).validateUsableWorks(any(), any());
        } else {
            works = null;
            problem = "No work number supplied.";
        }
        var val = makeVal(request);
        val.validateWorks();
        assertProblem(val.getProblems(), problem);
        if (anyWorks) {
            verify(mockWorkService).validateUsableWorks(any(), eq(request.getWorkNumbers()));
            assertThat(val.getWorks()).containsExactlyInAnyOrderElementsOf(works);
        } else {
            verifyNoInteractions(mockWorkService);
            assertThat(val.getWorks()).isEmpty();
        }
    }

    @Test
    void testValidateExternalBarcodes() {
        BlockRegisterRequest request = new BlockRegisterRequest();
        request.setLabware(Stream.of(null, "XB1", "xb1", "XBX", "LWX", "XB!")
                .map(TestBlockRegisterValidation::brlForXb).toList());
        mockStringValidator(mockExternalBarcodeValidator, "external barcode");
        when(mockLwRepo.findBarcodesByBarcodeIn(any())).thenReturn(Set.of("LWX"));
        when(mockLwRepo.findExternalBarcodesIn(any())).thenReturn(Set.of("XBX"));
        var val = makeVal(request);
        val.validateExternalBarcodes();
        Stream.of("XB1", "XBX", "LWX", "XB!")
                .forEach(s -> verify(mockExternalBarcodeValidator).validate(eq(s), any()));
        verifyNoMoreInteractions(mockExternalBarcodeValidator);
        verify(mockLwRepo).findBarcodesByBarcodeIn(any());
        verify(mockLwRepo).findExternalBarcodesIn(Set.of("XB1", "XBX", "XB!"));
        assertThat(val.getProblems()).containsExactlyInAnyOrder(
                "External barcode given multiple times: xb1",
                "Labware barcode already in use: [LWX]",
                "External barcode already in use: [XBX]",
                "Bad external barcode: XB!",
                "Missing external barcode."
        );
    }

    static BlockRegisterLabware brlForXb(String xb) {
        BlockRegisterLabware brl = new BlockRegisterLabware();
        brl.setExternalBarcode(xb);
        return brl;
    }

    @Test
    void testValidateAddresses() {
        final Address A1 = new Address(1,1), A2 = new Address(1,2), A3 = new Address(1,3);
        LabwareType lt = new LabwareType(1, "lt", 1, 1, null, false);
        BlockRegisterLabware brl1 = new BlockRegisterLabware();
        brl1.setLabwareType("LT");
        brl1.setSamples(List.of(
                brsForAddresses(A1, A2), brsForAddresses(A1, A3), new BlockRegisterSample()
        ));
        BlockRegisterLabware brl2 = new BlockRegisterLabware();
        brl2.setLabwareType("???");
        brl2.setSamples(List.of(brsForAddresses(A1, A2, A3)));
        BlockRegisterRequest request = new BlockRegisterRequest();
        request.setLabware(List.of(brl1, brl2));
        var val = makeVal(request);
        val.labwareTypeMap.put(lt.getName(), lt);

        val.validateAddresses();
        assertThat(val.getProblems()).containsExactlyInAnyOrder(
                "Slot addresses missing from request.",
                "Invalid slot addresses for labware type lt: [A2, A3]"
        );
    }

    static BlockRegisterSample brsForAddresses(Address... addresses) {
        BlockRegisterSample brs = new BlockRegisterSample();
        brs.setAddresses(List.of(addresses));
        return brs;
    }

    static void mockStringValidator(Validator<String> validator, String name) {
        doAnswer(invocation -> {
            String string = invocation.getArgument(0);
            if (string.indexOf('!') < 0) {
                return true;
            }
            Consumer<String> problemConsumer = invocation.getArgument(1);
            problemConsumer.accept("Bad "+name+": "+string);
            return false;
        }).when(validator).validate(any(), any());
    }
}