package uk.ac.sanger.sccp.stan.service.register;

import com.google.common.base.MoreObjects;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.verification.VerificationMode;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.register.BlockRegisterRequest;
import uk.ac.sanger.sccp.stan.request.register.RegisterRequest;
import uk.ac.sanger.sccp.stan.service.BioRiskService;
import uk.ac.sanger.sccp.stan.service.Validator;
import uk.ac.sanger.sccp.stan.service.register.RegisterValidationImp.StringIntKey;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;
import uk.ac.sanger.sccp.utils.Zip;

import java.time.LocalDate;
import java.util.*;
import java.util.function.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.EntityFactory.objToList;
import static uk.ac.sanger.sccp.stan.Matchers.assertProblem;

/**
 * Tests for {@link RegisterValidationImp}
 * @author dr6
 */
public class TestRegisterValidation {
    @Mock
    private DonorRepo mockDonorRepo;
    @Mock
    private HmdmcRepo mockHmdmcRepo;
    @Mock
    private TissueTypeRepo mockTtRepo;
    @Mock
    private LabwareTypeRepo mockLtRepo;
    @Mock
    private MediumRepo mockMediumRepo;
    @Mock
    private FixativeRepo mockFixativeRepo;
    @Mock
    private TissueRepo mockTissueRepo;
    @Mock
    private SpeciesRepo mockSpeciesRepo;
    @Mock
    private CellClassRepo mockCellClassRepo;
    @Mock
    private Validator<String> mockDonorNameValidation;
    @Mock
    private Validator<String> mockExternalNameValidation;
    @Mock
    private Validator<String> mockReplicateValidator;
    @Mock
    private TissueFieldChecker mockFieldChecker;
    @Mock
    private BioRiskService mockBioRiskService;
    @Mock
    private WorkService mockWorkService;

    private AutoCloseable mocking;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
    }

    private void loadSpecies(final Collection<Species> specieses) {
        when(mockSpeciesRepo.findByName(any())).then(invocation -> {
            String name = invocation.getArgument(0);
            return specieses.stream().filter(sp -> sp.getName().equalsIgnoreCase(name)).findAny();
        });
    }

    private RegisterValidationImp create(RegisterRequest request) {
        return spy(new RegisterValidationImp(request, mockDonorRepo, mockHmdmcRepo, mockTtRepo, mockLtRepo,
                mockMediumRepo, mockFixativeRepo, mockTissueRepo, mockSpeciesRepo, mockCellClassRepo,
                mockDonorNameValidation, mockExternalNameValidation, mockReplicateValidator, mockFieldChecker,
                mockBioRiskService, mockWorkService));
    }

    private void stubValidationMethods(RegisterValidationImp validation) {
        doNothing().when(validation).validateDonors();
        doNothing().when(validation).validateHmdmcs();
        doNothing().when(validation).validateSpatialLocations();
        doNothing().when(validation).validateLabwareTypes();
        doNothing().when(validation).validateMediums();
        doNothing().when(validation).validateExistingTissues();
        doNothing().when(validation).validateNewTissues();
        doNothing().when(validation).validateFixatives();
        doNothing().when(validation).validateCollectionDates();
        doNothing().when(validation).validateBioRisks();
        doNothing().when(validation).validateWorks();
        doNothing().when(validation).validateCellClasses();
    }

    private void verifyValidateMethods(RegisterValidationImp validation, VerificationMode verificationMode) {
        verify(validation, verificationMode).validateDonors();
        verify(validation, verificationMode).validateHmdmcs();
        verify(validation, verificationMode).validateSpatialLocations();
        verify(validation, verificationMode).validateLabwareTypes();
        verify(validation, verificationMode).validateMediums();
        verify(validation, verificationMode).validateExistingTissues();
        verify(validation, verificationMode).validateNewTissues();
        verify(validation, verificationMode).validateFixatives();
        verify(validation, verificationMode).validateCollectionDates();
        verify(validation, verificationMode).validateWorks();
        verify(validation, verificationMode).validateBioRisks();
        verify(validation, verificationMode).validateCellClasses();
    }

    @Test
    public void testValidateEmptyRequest() {
        RegisterRequest request = new RegisterRequest(List.of());
        RegisterValidationImp validation = create(request);
        stubValidationMethods(validation);

        assertThat(validation.validate()).isEmpty();
        verifyValidateMethods(validation, never());
    }

    @Test
    public void testValidateNonemptyRequestWithoutProblems() {
        RegisterRequest request = new RegisterRequest(List.of(new BlockRegisterRequest()));
        RegisterValidationImp validation = create(request);
        stubValidationMethods(validation);
        assertThat(validation.validate()).isEmpty();
        verifyValidateMethods(validation, times(1));
    }

    @Test
    public void testValidateNonemptyRequestWithProblems() {
        RegisterRequest request = new RegisterRequest(List.of(new BlockRegisterRequest()));
        RegisterValidationImp validation = create(request);
        stubValidationMethods(validation);
        doAnswer(invocation -> validation.problems.add("Problem alpha."))
                .when(validation).validateDonors();
        doAnswer(invocation -> validation.problems.add("Problem beta."))
                .when(validation).validateHmdmcs();
        assertThat(validation.validate()).hasSameElementsAs(List.of("Problem alpha.", "Problem beta."));
        verifyValidateMethods(validation, times(1));
    }

    @ParameterizedTest
    @MethodSource("donorData")
    public void testValidateDonors(List<String> donorNames, List<LifeStage> lifeStages, List<String> speciesNames,
                                   List<Donor> knownDonors, List<Species> knownSpecies, List<Donor> expectedDonors,
                                   List<String> expectedProblems) {
        loadSpecies(knownSpecies);
        Iterator<LifeStage> lifeStageIter = lifeStages.listIterator();
        Iterator<String> speciesIter = speciesNames.iterator();
        RegisterRequest request = new RegisterRequest(
                donorNames.stream()
                        .map(donorName -> {
                            BlockRegisterRequest br = new BlockRegisterRequest();
                            br.setDonorIdentifier(donorName);
                            br.setLifeStage(lifeStageIter.next());
                            br.setSpecies(speciesIter.next());
                            return br;
                        })
                        .collect(toList())
        );
        when(mockDonorRepo.findByDonorName(any())).then(invocation -> {
            final String name = invocation.getArgument(0);
            return knownDonors.stream().filter(d -> name.equalsIgnoreCase(d.getDonorName())).findAny();
        });

        RegisterValidationImp validation = create(request);
        validation.validateDonors();
        assertThat(validation.getProblems()).hasSameElementsAs(expectedProblems);
        Map<String, Donor> expectedDonorMap = expectedDonors.stream().collect(toMap(d -> d.getDonorName().toUpperCase(), d -> d));
        assertEquals(expectedDonorMap, validation.donorMap);
        expectedDonors.forEach(donor ->
                assertEquals(donor, validation.getDonor(donor.getDonorName()))
        );
    }

    @Test
    public void testDonorNameValidation() {
        when(mockDonorNameValidation.validate(any(), any())).then(invocation -> {
            String name = invocation.getArgument(0);
            Consumer<String> addProblem = invocation.getArgument(1);
            if (name.contains("*")) {
                addProblem.accept("Invalid name: "+name);
                return false;
            }
            return true;
        });
        loadSpecies(List.of(EntityFactory.getHuman()));
        RegisterRequest request = new RegisterRequest(
                Stream.of("Alpha", "Beta", "Gamma*", "Delta*", "Gamma*")
                .map(s -> {
                    BlockRegisterRequest br = new BlockRegisterRequest();
                    br.setDonorIdentifier(s);
                    br.setLifeStage(LifeStage.adult);
                    br.setSpecies("Human");
                    return br;
                })
                .collect(toList())
        );
        RegisterValidationImp validation = create(request);
        validation.validateDonors();
        assertThat(validation.getProblems()).hasSameElementsAs(List.of("Invalid name: Gamma*", "Invalid name: Delta*"));
    }

    @ParameterizedTest
    @MethodSource("slData")
    public void testValidateSpatialLocations(List<String> tissueTypeNames, List<Integer> codes,
                                             List<TissueType> knownTissueTypes, List<SpatialLocation> expectedSLs,
                                             List<String> expectedProblems) {
        RegisterRequest request = new RegisterRequest(
                Zip.map(tissueTypeNames.stream(), codes.stream(),
                (name, code) -> {
                    BlockRegisterRequest br = new BlockRegisterRequest();
                    br.setTissueType(name);
                    br.setSpatialLocation(code);
                    return br;
                }).toList());
        when(mockTtRepo.findByName(any())).then(invocation -> {
            final String arg = invocation.getArgument(0);
            return knownTissueTypes.stream().filter(tt -> arg.equalsIgnoreCase(tt.getName())).findAny();
        });

        RegisterValidationImp validation = create(request);
        validation.validateSpatialLocations();
        assertThat(validation.getProblems()).hasSameElementsAs(expectedProblems);
        Map<StringIntKey, SpatialLocation> expectedSLMap = expectedSLs.stream()
                .collect(toMap(sl -> new StringIntKey(sl.getTissueType().getName(), sl.getCode()), sl -> sl));
        assertEquals(expectedSLMap, validation.spatialLocationMap);
        expectedSLs.forEach(sl ->
                assertEquals(sl, validation.getSpatialLocation(sl.getTissueType().getName(), sl.getCode()))
        );
    }

    @ParameterizedTest
    @MethodSource("hmdmcData")
    public void testValidateHmdmcs(List<Hmdmc> knownHmdmcs, List<String> givenHmdmcs, List<String> speciesNames,
                                   List<Hmdmc> expectedHmdmcs, List<String> expectedProblems) {
        when(mockHmdmcRepo.findByHmdmc(any())).then(invocation -> {
            final String arg = invocation.getArgument(0);
            return knownHmdmcs.stream().filter(h -> arg.equalsIgnoreCase(h.getHmdmc())).findAny();
        });

        RegisterRequest request = new RegisterRequest(
                Zip.map(givenHmdmcs.stream(), speciesNames.stream(),
                        (hmdmc, species) -> {
                            BlockRegisterRequest br = new BlockRegisterRequest();
                            br.setHmdmc(hmdmc);
                            br.setSpecies(species);
                            return br;
                        })
                        .toList()
        );

        RegisterValidationImp validation = create(request);
        validation.validateHmdmcs();
        assertThat(validation.getProblems()).hasSameElementsAs(expectedProblems);
        Map<String, Hmdmc> expectedItemMap = expectedHmdmcs.stream().collect(toMap(item -> item.getHmdmc().toUpperCase(), h -> h));
        Map<String, Hmdmc> actualMap = validation.hmdmcMap.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
        assertEquals(expectedItemMap, actualMap);
        for (Hmdmc hmdmc : expectedHmdmcs) {
            assertEquals(hmdmc, validation.getHmdmc(hmdmc.getHmdmc()));
        }
    }

    @ParameterizedTest
    @MethodSource("ltData")
    public void testValidateLabwareTypes(List<LabwareType> knownLts, List<String> givenLtNames,
                                         List<LabwareType> expectedLts, List<String> expectedProblems) {
        when(mockLtRepo.findByName(any())).then(invocation -> {
            final String arg = invocation.getArgument(0);
            return knownLts.stream().filter(lt -> arg.equalsIgnoreCase(lt.getName())).findAny();
        });
        testValidateSimpleField(givenLtNames, expectedLts, expectedProblems,
                RegisterValidationImp::validateLabwareTypes, LabwareType::getName, BlockRegisterRequest::setLabwareType,
                v -> v.labwareTypeMap, RegisterValidationImp::getLabwareType);
    }

    @ParameterizedTest
    @MethodSource("mediumData")
    public void testValidateMediums(List<Medium> knownItems, List<String> givenNames,
                                       List<Medium> expectedItems, List<String> expectedProblems) {
        when(mockMediumRepo.findByName(any())).then(invocation -> {
            final String arg = invocation.getArgument(0);
            return knownItems.stream().filter(item -> arg.equalsIgnoreCase(item.getName())).findAny();
        });
        testValidateSimpleField(givenNames, expectedItems, expectedProblems,
                RegisterValidationImp::validateMediums, Medium::getName, BlockRegisterRequest::setMedium,
                v -> v.mediumMap, RegisterValidationImp::getMedium);
    }

    @ParameterizedTest
    @MethodSource("fixativeData")
    public void testValidateFixatives(List<Fixative> knownItems, List<String> givenNames,
                                      List<Fixative> expectedItems, List<String> expectedProblems) {
        when(mockFixativeRepo.findByName(any())).then(invocation -> {
            final String arg = invocation.getArgument(0);
            return knownItems.stream().filter(item -> arg.equalsIgnoreCase(item.getName())).findAny();
        });
        testValidateSimpleField(givenNames, expectedItems, expectedProblems,
                RegisterValidationImp::validateFixatives, Fixative::getName, BlockRegisterRequest::setFixative,
                v -> v.fixativeMap, RegisterValidationImp::getFixative);
    }

    @ParameterizedTest
    @MethodSource("newTissueData")
    void testValidateNewTissues(final List<ValidateTissueTestData> testData, List<String> expectedProblems) {
        when(mockExternalNameValidation.validate(any(), any())).then(invocation -> {
            String name = invocation.getArgument(0);
            Consumer<String> addProblem = invocation.getArgument(1);
            if (name.contains("*")) {
                addProblem.accept("Invalid name: " + name);
                return true;
            }
            return false;
        });
        when(mockTissueRepo.findAllByExternalName(anyString())).then(invocation -> {
            final String name = invocation.getArgument(0);
            return testData.stream()
                    .filter(td -> td.anyWithSameIdentifier && td.externalName.equalsIgnoreCase(name))
                    .map(td -> EntityFactory.getTissue())
                    .collect(toList());
        });
        when(mockReplicateValidator.validate(any(), any())).then(invocation -> {
            String replicate = invocation.getArgument(0);
            Consumer<String> addProblem = invocation.getArgument(1);
            if (!replicate.matches("\\d+[a-zA-Z]?")) {
                addProblem.accept("Invalid replicate: " + replicate);
                return true;
            }
            return false;
        });

        RegisterRequest request = new RegisterRequest(
                testData.stream()
                .map(td -> {
                    BlockRegisterRequest br = new BlockRegisterRequest();
                    br.setExternalIdentifier(td.externalName);
                    br.setReplicateNumber(td.replicate);
                    br.setDonorIdentifier(td.donorName);
                    br.setTissueType(td.tissueTypeName);
                    br.setSpatialLocation(td.slCode);
                    br.setMedium(td.mediumName);
                    br.setHighestSection(td.highestSection);
                    br.setFixative(td.fixativeName);
                    br.setExistingTissue(td.existing);
                    return br;
                })
                .collect(toList())
        );

        RegisterValidationImp validation = create(request);

        validation.validateNewTissues();
        assertThat(validation.getProblems()).hasSameElementsAs(expectedProblems);
    }

    @ParameterizedTest
    @MethodSource("validateExistingTissuesArgs")
    public void testValidateExistingTissue(Object testDataObj, Object existingTissuesObj,
                                           Object expectedProblemsObj) {
        final List<ValidateExistingTissueTestData> testData = objToList(testDataObj);
        final List<Tissue> existingTissues = objToList(existingTissuesObj);
        final List<String> expectedProblems = objToList(expectedProblemsObj);
        when(mockTissueRepo.findAllByExternalNameIn(any())).then(invocation -> {
            Collection<String> xns = invocation.getArgument(0);
            return existingTissues.stream().filter(t -> xns.stream().anyMatch(xn -> t.getExternalName().equalsIgnoreCase(xn)))
                    .collect(toList());
        });
        List<BlockRegisterRequest> brs = new ArrayList<>(testData.size());
        for (ValidateExistingTissueTestData td : testData) {
            BlockRegisterRequest br = new BlockRegisterRequest();
            br.setExternalIdentifier(td.externalName);
            br.setExistingTissue(td.existing);
            if (td.fieldProblem != null) {
                doAnswer(invocation -> {
                    Consumer<String> problemConsumer = invocation.getArgument(0);
                    problemConsumer.accept(td.fieldProblem);
                    return null;
                }).when(mockFieldChecker).check(any(), same(br), any());
            }
            brs.add(br);
        }
        RegisterRequest request = new RegisterRequest(brs);

        RegisterValidationImp validation = create(request);
        validation.validateExistingTissues();
        assertThat(validation.getProblems()).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> validateExistingTissuesArgs() {
        Tissue tissue = EntityFactory.getTissue();
        Tissue tissue2 = EntityFactory.makeTissue(tissue.getDonor(), tissue.getSpatialLocation());
        return Stream.of(
                Arguments.of(List.of(ValidateExistingTissueTestData.externalName("X1").existing(false),
                        ValidateExistingTissueTestData.externalName(tissue.getExternalName())), tissue, null),
                Arguments.of(ValidateExistingTissueTestData.externalName("X1").existing(false), null, null),

                Arguments.of(List.of(ValidateExistingTissueTestData.externalName(null),
                        ValidateExistingTissueTestData.externalName(tissue.getExternalName())),
                        tissue, "Missing external identifier."),
                Arguments.of(List.of(ValidateExistingTissueTestData.externalName("Bananas"),
                        ValidateExistingTissueTestData.externalName("Golf")),
                        null, "Existing external identifiers not recognised: [\"Bananas\", \"Golf\"]"),
                Arguments.of(List.of(ValidateExistingTissueTestData.externalName(tissue.getExternalName()).fieldProblem("Bad tissue type."),
                        ValidateExistingTissueTestData.externalName(tissue2.getExternalName()).fieldProblem("Bad spatial location.")),
                        List.of(tissue, tissue2), List.of("Bad tissue type.", "Bad spatial location."))
        );
    }

    @ParameterizedTest
    @MethodSource("validateCollectionDatesArgs")
    public void testValidateCollectionDates(List<BlockRegisterRequest> brrs, List<String> expectedProblems) {
        RegisterRequest request = new RegisterRequest(brrs);
        RegisterValidationImp validation = create(request);
        validation.validateCollectionDates();
        assertThat(validation.getProblems()).containsExactlyInAnyOrderElementsOf(expectedProblems);
    }

    static Stream<Arguments> validateCollectionDatesArgs() {
        LocalDate future1 = LocalDate.now().plusDays(7);
        LocalDate future2 = future1.plusDays(2);
        return Arrays.stream(new Object[][]{
                { toBrr("Human", LifeStage.fetal, LocalDate.of(2022,1,20)) },
                { toBrr("Hamster", LifeStage.fetal, null) },
                { toBrr("Human", LifeStage.adult, null) },
                { toBrr("Human", LifeStage.paediatric, null) },

                { toBrr("Human", LifeStage.fetal, LocalDate.of(2021,5,6)),
                        toBrr("Human", LifeStage.paediatric, null),
                        toBrr("Hamster", LifeStage.fetal, null),
                        toBrr(null, LifeStage.fetal, null),
                        toBrr("Human", null, null),
                },

                { toBrr("Human", LifeStage.fetal, null),
                        "Human fetal samples must have a collection date." },
                { toBrr("Human", LifeStage.fetal, future1),
                        toBrr("Hamster", LifeStage.adult, future2),
                        "Invalid sample collection dates: ["+future1+", "+future2+"]"},
                { toBrr("Human", LifeStage.fetal, LocalDate.of(2021,1,2), "Ext1"),
                        toBrr("Human", LifeStage.fetal, LocalDate.of(2021,1,3), "ext1"),
                        "Inconsistent collection dates specified for tissue EXT1."},

                { toBrr("Human", LifeStage.fetal, LocalDate.of(2020,2,1), "EXT2"),
                        toBrr("Human", LifeStage.adult, LocalDate.of(2020,2,2), "ext1"),
                                toBrr("Human", LifeStage.adult, null, "ext1"),
                        toBrr(null, null, null),
                        toBrr("Human", LifeStage.fetal, null),
                        toBrr("Human", LifeStage.adult, future1),
                        "Human fetal samples must have a collection date.",
                        "Invalid sample collection date: ["+future1+"]",
                        "Inconsistent collection dates specified for tissue EXT1."},
        }).map(arr -> Arguments.of(Arrays.stream(arr).filter(obj -> obj instanceof BlockRegisterRequest)
                .collect(toList()),
                Arrays.stream(arr).filter(obj -> obj instanceof String)
                .collect(toList())));
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "true,true"})
    public void testValidateWorks(boolean anyWorks, boolean anyProblem) {
        List<BlockRegisterRequest> brrs = List.of(
                toBrr("Human", LifeStage.adult, null)
        );
        List<String> workNumbers;
        List<Work> works;
        if (!anyWorks) {
            workNumbers = List.of();
            works = List.of();
        } else {
            workNumbers = List.of("SGP1", "SGP2");
            works = IntStream.rangeClosed(1,2).mapToObj(i -> {
                Work w = new Work();
                w.setId(i);
                w.setWorkNumber("SGP"+i);
                return w;
            }).collect(toList());
        }

        RegisterRequest request = new RegisterRequest(brrs, workNumbers);
        var validation = create(request);
        if (anyProblem) {
            when(mockWorkService.validateUsableWorks(any(), any())).then(
                    Matchers.addProblem("Bad work", UCMap.from(works, Work::getWorkNumber))
            );
        } else if (anyWorks) {
            when(mockWorkService.validateUsableWorks(any(), any())).thenReturn(UCMap.from(works, Work::getWorkNumber));
        }
        validation.validateWorks();
        if (!anyWorks) {
            verifyNoInteractions(mockWorkService);
            assertProblem(validation.getProblems(), "No work number supplied.");
        } else {
            if (anyProblem) {
                assertThat(validation.getProblems()).containsExactly("Bad work");
            } else {
                assertThat(validation.getProblems()).isEmpty();
            }
            verify(mockWorkService).validateUsableWorks(any(), eq(workNumbers));
        }
        assertThat(validation.getWorks()).containsExactlyInAnyOrderElementsOf(works);

    }

    static BlockRegisterRequest toBrr(String species, LifeStage lifeStage, LocalDate collectionDate, String ext) {
        BlockRegisterRequest brr = new BlockRegisterRequest();
        brr.setSpecies(species);
        brr.setLifeStage(lifeStage);
        brr.setSampleCollectionDate(collectionDate);
        brr.setExternalIdentifier(ext);
        return brr;
    }

    static BlockRegisterRequest toBrr(String species, LifeStage lifeStage, LocalDate collectionDate) {
        return toBrr(species, lifeStage, collectionDate, null);
    }

    private <E> void testValidateSimpleField(List<String> givenStrings,
                                   List<E> expectedItems, List<String> expectedProblems,
                                            Consumer<RegisterValidationImp> validationFunction,
                                            Function<E, String> stringFn,
                                            BiConsumer<BlockRegisterRequest, String> blockFunction,
                                            Function<RegisterValidationImp, Map<String, E>> mapFunction,
                                            BiFunction<RegisterValidationImp, String, E> getter) {
        RegisterRequest request = new RegisterRequest(
                givenStrings.stream()
                        .map(string -> {
                            BlockRegisterRequest br = new BlockRegisterRequest();
                            blockFunction.accept(br, string);
                            return br;
                        })
                        .collect(toList()));

        RegisterValidationImp validation = create(request);
        validationFunction.accept(validation);
        assertThat(validation.getProblems()).hasSameElementsAs(expectedProblems);
        Map<String, E> expectedItemMap = expectedItems.stream().collect(toMap(item -> stringFn.apply(item).toUpperCase(), h -> h));
        Map<String, E> actualMap = mapFunction.apply(validation).entrySet().stream()
                .filter(e -> e.getValue()!=null)
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
        assertEquals(expectedItemMap, actualMap);
        for (E item : expectedItems) {
            assertEquals(item, getter.apply(validation, stringFn.apply(item)));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void testValidateBioRisks() {
        BlockRegisterRequest block1 = new BlockRegisterRequest();
        block1.setBioRiskCode("risk1");
        BlockRegisterRequest block2 = new BlockRegisterRequest();
        block2.setBioRiskCode("risk2");
        RegisterRequest request = new RegisterRequest(List.of(block1, block2));
        RegisterValidationImp val = create(request);
        UCMap<BioRisk> returnedMap = UCMap.from(BioRisk::getCode, new BioRisk(1, "risk1"));
        when(mockBioRiskService.loadAndValidateBioRisks(any(), any(), any(), any())).thenReturn(returnedMap);

        val.validateBioRisks();

        assertSame(returnedMap, val.bioRiskMap);
        ArgumentCaptor<Stream<BlockRegisterRequest>> blockStreamCaptor = ArgumentCaptor.forClass(Stream.class);
        ArgumentCaptor<Function<BlockRegisterRequest, String>> getterCaptor = ArgumentCaptor.forClass(Function.class);
        ArgumentCaptor<BiConsumer<BlockRegisterRequest, String>> setterCaptor = ArgumentCaptor.forClass(BiConsumer.class);
        verify(mockBioRiskService).loadAndValidateBioRisks(same(val.problems), blockStreamCaptor.capture(),
                getterCaptor.capture(), setterCaptor.capture());

        // Check that the getter and setter are the functions we expect
        assertThat(blockStreamCaptor.getValue().map(getterCaptor.getValue())).containsExactly("risk1", "risk2");
        BiConsumer<BlockRegisterRequest, String> setter = setterCaptor.getValue();
        BlockRegisterRequest blk = new BlockRegisterRequest();
        setter.accept(blk, "v1");
        assertEquals("v1", blk.getBioRiskCode());
    }

    @Test
    void testValidateCellClasses() {
        String[] ccNames = {"cc1", "cc2", null, "cc4"};
        List<BlockRegisterRequest> blocks = IntStream.range(0, ccNames.length).mapToObj(i -> new BlockRegisterRequest()).toList();
        Zip.forEach(Arrays.stream(ccNames), blocks.stream(), (name, block) -> block.setCellClass(name));
        CellClass[] cellClasses = IntStream.rangeClosed(1, 2).mapToObj(i -> new CellClass(i, "cc"+i, false, true)).toArray(CellClass[]::new);
        UCMap<CellClass> ccMap = UCMap.from(CellClass::getName, cellClasses);
        when(mockCellClassRepo.findMapByNameIn(any())).thenReturn(ccMap);
        RegisterRequest request = new RegisterRequest(blocks);
        RegisterValidationImp val = create(request);
        val.validateCellClasses();
        assertThat(val.problems).containsExactlyInAnyOrder("Missing cell class name.", "Unknown cell class name: [\"cc4\"]");
        verify(mockCellClassRepo).findMapByNameIn(Set.of("cc1", "cc2", "cc4"));
        assertSame(ccMap, val.cellClassMap);
    }

    /** @see #testValidateDonors */
    private static Stream<Arguments> donorData() {
        Species human = new Species(1, "Human");
        Species hamster = new Species(2, "Hamster");
        Species dodo = new Species(3, "Dodo");
        dodo.setEnabled(false);
        List<Species> knownSpecies = List.of(human, hamster, dodo);
        Donor dirk = new Donor(1, "Dirk", LifeStage.adult, human);
        Donor jeff = new Donor(2, "Jeff", LifeStage.fetal, hamster);
        Donor dodonor = new Donor(3, "Dodonor", LifeStage.adult, dodo);
        // List<String> donorNames, List<LifeStage> lifeStages, List<String> speciesNames,
        // List<Donor> knownDonors, List<Species> knownSpecies, List<Donor> expectedDonors,
        // List<String> expectedProblems
        return Stream.of(
                // Valid:
                Arguments.of(List.of("DONOR1", "Donor2"), List.of(LifeStage.adult, LifeStage.fetal),
                        List.of("human", "hamster"),
                        List.of(), knownSpecies,
                        List.of(new Donor(null, "DONOR1", LifeStage.adult, human),
                                new Donor(null, "Donor2", LifeStage.fetal, hamster)),
                        List.of()),
                Arguments.of(List.of("Donor1", "DONOR1"), List.of(LifeStage.adult, LifeStage.adult),
                        List.of("human", "human"),
                        List.of(), knownSpecies,
                        List.of(new Donor(null, "Donor1", LifeStage.adult, human)),
                        List.of()),
                Arguments.of(List.of("DIRK", "jeff"),
                        List.of(dirk.getLifeStage(), jeff.getLifeStage()), List.of("human", "hamster"),
                        List.of(dirk, jeff), knownSpecies, List.of(dirk, jeff),
                        List.of()),

                // Invalid:
                Arguments.of(List.of("Dirk", "jeff"), List.of(LifeStage.adult, LifeStage.paediatric),
                        List.of("human", "hamster"),
                        List.of(dirk, jeff), knownSpecies, List.of(dirk, jeff),
                        List.of("Wrong life stage given for existing donor Jeff")),
                Arguments.of(List.of("Donor1", "DONOR1"), List.of(LifeStage.adult, LifeStage.fetal),
                        List.of("human", "human"),
                        List.of(), knownSpecies, List.of(new Donor(null, "Donor1", LifeStage.adult, human)),
                        List.of("Multiple different life stages specified for donor Donor1")),
                Arguments.of(Arrays.asList(null, null), Arrays.asList(null, null),
                        List.of("human", "human"),
                        List.of(), knownSpecies, List.of(), List.of("Missing donor identifier.")),
                Arguments.of(List.of(""), List.of(LifeStage.adult),
                        List.of("human", "human"),
                        List.of(), knownSpecies, List.of(), List.of("Missing donor identifier.")),
                Arguments.of(List.of("Donor1"), List.of(LifeStage.adult),
                        List.of(""), List.of(), knownSpecies, List.of(new Donor(null, "Donor1", LifeStage.adult, null)),
                        List.of("Missing species.")),
                Arguments.of(List.of("Donor1"), List.of(LifeStage.adult), List.of("Bananas"),
                        List.of(), knownSpecies, List.of(new Donor(null, "Donor1", LifeStage.adult, null)),
                                List.of("Unknown species: \"Bananas\"")),
                Arguments.of(List.of("Donor1", "DONOR1"), List.of(LifeStage.adult, LifeStage.adult),
                        List.of("human", "hamster"),
                        List.of(), knownSpecies, List.of(new Donor(null, "Donor1", LifeStage.adult, human)),
                        List.of("Multiple different species specified for donor Donor1")),
                Arguments.of(List.of("Donor1", "Jeff"), List.of(LifeStage.adult, LifeStage.fetal),
                        List.of("human", "human"),
                        List.of(jeff), knownSpecies, List.of(jeff, new Donor(null, "Donor1", LifeStage.adult, human)),
                        List.of("Wrong species given for existing donor Jeff")),
                Arguments.of(List.of("dodonor"),
                        List.of(dodonor.getLifeStage()), List.of("dodo"),
                        List.of(dodonor), knownSpecies, List.of(dodonor),
                        List.of("Species is not enabled: Dodo")),
                Arguments.of(List.of("Donor1"), List.of(LifeStage.adult), List.of("dodo"), List.of(), knownSpecies,
                        List.of(new Donor(null, "Donor1", LifeStage.adult, dodo)),
                        List.of("Species is not enabled: Dodo")),

                Arguments.of(List.of("Donor1", "DONOR1", "jeff", "dirk", "", ""),
                        List.of(LifeStage.adult, LifeStage.fetal, LifeStage.paediatric, LifeStage.paediatric, LifeStage.adult, LifeStage.adult),
                        List.of("human", "human", "hamster", "human", "human", "human"),
                        List.of(dirk, jeff),knownSpecies, List.of(new Donor(null, "Donor1", LifeStage.adult, human), dirk, jeff),
                        List.of("Multiple different life stages specified for donor Donor1",
                                "Wrong life stage given for existing donor Dirk",
                                "Wrong life stage given for existing donor Jeff",
                                "Missing donor identifier."))

        );
    }

    private static Stream<Arguments> slData() {
        final TissueType tt = new TissueType(50, "Arm", "ARM");
        final String name = tt.getName();
        final SpatialLocation sl0 = new SpatialLocation(500, "Alpha", 0, tt);
        final SpatialLocation sl1 = new SpatialLocation(501, "Beta", 1, tt);
        final SpatialLocation sl2 = new SpatialLocation(502, "Gamma", 2, tt);
        sl2.setEnabled(false);
        tt.setSpatialLocations(List.of(sl0, sl1, sl2));
        final TissueType tt2 = new TissueType(51, "Leg", "LEG");
        tt2.setEnabled(false);
        final SpatialLocation sl0a = new SpatialLocation(601, "Alabama", 0, tt2);
        tt2.setSpatialLocations(List.of(sl0a));
        return Stream.of(
                // Valid:
                Arguments.of(List.of(name, name.toUpperCase(), name.toLowerCase()), List.of(0, 0, 1),
                        List.of(tt), List.of(sl0, sl1), List.of()),

                // Invalid:
                Arguments.of(List.of(name, "Plumbus", "Slime", "Slime"), List.of(1, 2, 3, 4),
                        List.of(tt), List.of(sl1), List.of("Unknown tissue types: [Plumbus, Slime]")),
                Arguments.of(List.of(name, name, name), List.of(0,1,2), List.of(tt), List.of(sl0, sl1, sl2),
                        List.of("Spatial location is disabled: 2 for tissue type Arm.")),
                Arguments.of(List.of(tt2.getName()), List.of(0), List.of(tt2), List.of(sl0a),
                        List.of("Tissue type \""+tt2.getName()+"\" is disabled.")),
                Arguments.of(List.of(name, name, name, name, name, name), List.of(0, 0, 1, 3, 3, 4),
                        List.of(tt), List.of(sl0, sl1), List.of("Unknown spatial location 3 for tissue type Arm.",
                                "Unknown spatial location 4 for tissue type Arm.")),
                Arguments.of(Arrays.asList(null,  null), List.of(1,2),
                        List.of(tt), List.of(), List.of("Missing tissue type.")),
                Arguments.of(List.of(name, "", "Plumbus", name), List.of(0, 0, 0, 5),
                        List.of(tt), List.of(sl0),
                        List.of("Missing tissue type.", "Unknown tissue type: [Plumbus]",
                                "Unknown spatial location 5 for tissue type Arm."))
        );
    }

    /** @see #testValidateHmdmcs */
    private static Stream<Arguments> hmdmcData() {
        Hmdmc h0 = new Hmdmc(20000, "20/000");
        Hmdmc h1 = new Hmdmc(20001, "20/001");
        Hmdmc h2 = new Hmdmc(20002, "20/002");
        Hmdmc h3 = new Hmdmc(20003, "20/003");
        h2.setEnabled(false);
        h3.setEnabled(false);
        // List<Hmdmc> knownHmdmcs, List<String> givenHmdmcs, List<String> speciesNames,
        // List<Hmdmc> expectedHmdmcs, List<String> expectedProblems
        return Stream.of(
                Arguments.of(List.of(h0, h1), List.of("20/001", "20/000", "20/000", ""), List.of("Human", "Human", "Human", "Hamster"),
                        List.of(h0, h1), List.of()),
                Arguments.of(List.of(h0, h1), List.of("20/001", "20/404", "20/405"), List.of("Human", "Human", "Human"),
                        List.of(h1), List.of("Unknown HuMFre numbers: [20/404, 20/405]")),
                Arguments.of(List.of(h0), Arrays.asList(null, "20/000", null), List.of("Human", "Human", "Human"),
                        List.of(h0), List.of("Missing HuMFre number.")),
                Arguments.of(List.of(h0, h1), List.of("20/000", "20/001"), List.of("Human", "Hamster"),
                        List.of(h0), List.of("Non-human tissue should not have a HuMFre number.")),
                Arguments.of(List.of(h0, h2, h3), List.of("20/000", "20/002", "20/003", "20/002"), List.of("Human", "Human", "Human", "Human"),
                        List.of(h0, h2, h3), List.of("HuMFre numbers not enabled: [20/002, 20/003]")),
                Arguments.of(List.of(h0, h1, h2), List.of("20/000", "20/001", "20/000", "", "", "20/404", "20/002"),
                        List.of("Human", "Human", "Human", "Human", "Human", "Human", "Human"),
                        List.of(h0, h1, h2), List.of("Missing HuMFre number.", "Unknown HuMFre number: [20/404]",
                                "HuMFre number not enabled: [20/002]"))
        );
    }

    private static Stream<Arguments> ltData() {
        LabwareType lt0 = EntityFactory.getTubeType();
        LabwareType lt1 = EntityFactory.makeLabwareType(2, 3);
        String name0 = lt0.getName();
        String name1 = lt1.getName();
        return Stream.of(
                Arguments.of(List.of(lt0, lt1), List.of(name1, name0, name0), List.of(lt0, lt1), List.of()),
                Arguments.of(List.of(lt0, lt1), List.of(name1, "Custard", "Banana"), List.of(lt1), List.of("Unknown labware types: [Custard, Banana]")),
                Arguments.of(List.of(lt0), Arrays.asList(null, name0, null), List.of(lt0), List.of("Missing labware type.")),
                Arguments.of(List.of(lt0, lt1), List.of(name0, name1, name0, "", "", "Banana"), List.of(lt0, lt1), List.of("Missing labware type.", "Unknown labware type: [Banana]"))
        );
    }

    private static Stream<Arguments> mediumData() {
        Medium med = EntityFactory.getMedium();
        return Stream.of(Arguments.of(List.of(med), List.of(med.getName()), List.of(med), List.of()),
                Arguments.of(List.of(), Arrays.asList(null, ""), List.of(), List.of("Missing medium.")),
                Arguments.of(List.of(med), List.of(med.getName(), "Sausage", "Sausage"), List.of(med), List.of("Unknown medium: [Sausage]")));
    }


    private static Stream<Arguments> fixativeData() {
        Fixative fix = EntityFactory.getFixative();
        return Stream.of(Arguments.of(List.of(fix), List.of(fix.getName()), List.of(fix), List.of()),
                Arguments.of(List.of(), Arrays.asList(null, ""), List.of(), List.of("Missing fixative.")),
                Arguments.of(List.of(fix), List.of(fix.getName(), "Sausage", "Sausage"), List.of(fix), List.of("Unknown fixative: [Sausage]")));
    }


    private static Stream<Arguments> newTissueData() {
        return Stream.of(
                // No problems
                Arguments.of(List.of(ValidateTissueTestData.externalName("X1").replicate("1"),
                        ValidateTissueTestData.externalName("X2").replicate("2a")),
                        List.of()),
                Arguments.of(List.of(ValidateTissueTestData.externalName("X1").replicate("1").anyWithSameIdentifier(true).existing(true),
                        ValidateTissueTestData.externalName("X2").replicate("2a")),
                        List.of()),

                // Some problems
                Arguments.of(List.of(ValidateTissueTestData.externalName("X1").replicate("1"),
                        ValidateTissueTestData.externalName("X2").replicate("-4")),
                        List.of("Invalid replicate: -4")),
                Arguments.of(List.of(ValidateTissueTestData.externalName("X1").replicate("1"),
                        ValidateTissueTestData.externalName("X2").replicate("2").highestSection(-2)),
                        List.of("Highest section number cannot be negative.")),
                Arguments.of(List.of(ValidateTissueTestData.externalName(null)),
                        List.of("Missing external identifier.")),
                Arguments.of(List.of(ValidateTissueTestData.externalName("")),
                        List.of("Missing external identifier.")),
                Arguments.of(List.of(ValidateTissueTestData.externalName("Banana*")),
                        List.of("Invalid name: Banana*")),
                Arguments.of(List.of(ValidateTissueTestData.externalName("xyz").replicate("1"),
                        ValidateTissueTestData.externalName("Xyz").replicate("2")),
                        List.of("Repeated external identifier: Xyz")),
                Arguments.of(List.of(ValidateTissueTestData.externalName("X1").replicate("1"),
                        ValidateTissueTestData.externalName("X2").replicate("2").anyWithSameIdentifier(true)),
                        List.of("There is already tissue in the database with external identifier X2.")),
                Arguments.of(List.of(ValidateTissueTestData.externalName("X1").replicate("1").anyWithSameIdentifier(true),
                        ValidateTissueTestData.externalName("X1").replicate("2").anyWithSameIdentifier(true),
                        ValidateTissueTestData.externalName("X2").replicate("3").anyWithSameIdentifier(true)),
                        List.of("There is already tissue in the database with external identifier X1.",
                                "There is already tissue in the database with external identifier X2.",
                                "Repeated external identifier: X1")),

                // Many problems
                Arguments.of(List.of(ValidateTissueTestData.externalName("X1*").replicate("-1").highestSection(-1),
                        ValidateTissueTestData.externalName(null).replicate("2"),
                        ValidateTissueTestData.externalName("X1").replicate("3").anySimilarInDatabase(true),
                        ValidateTissueTestData.externalName("X2").replicate("4").anyWithSameIdentifier(true),
                        ValidateTissueTestData.externalName("X3").replicate("5"),
                        ValidateTissueTestData.externalName("X4").replicate("5"),
                        ValidateTissueTestData.externalName("X4").replicate("6")),
                        List.of("Invalid replicate: -1",
                                "Highest section number cannot be negative.",
                                "Missing external identifier.",
                                "Invalid name: X1*",
                                "Repeated external identifier: X4",
                                "There is already tissue in the database with external identifier X2."))
        );
    }

    static class ValidateTissueTestData {
        String externalName;
        String replicate = "1";
        String donorName = "D";
        String tissueTypeName = "TT";
        int slCode = 2;
        String mediumName = "M";
        String fixativeName = "F";
        int highestSection = 0;
        boolean anySimilarInDatabase;
        boolean anyWithSameIdentifier;
        boolean existing;

        public ValidateTissueTestData(String externalName) {
            this.externalName = externalName;
        }

        public static ValidateTissueTestData externalName(String externalName) {
            return new ValidateTissueTestData(externalName);
        }

        public ValidateTissueTestData replicate(String replicate) {
            this.replicate = replicate;
            return this;
        }

        public ValidateTissueTestData highestSection(int highestSection) {
            this.highestSection = highestSection;
            return this;
        }

        public ValidateTissueTestData anySimilarInDatabase(boolean anySimilarInDatabase) {
            this.anySimilarInDatabase = anySimilarInDatabase;
            return this;
        }

        public ValidateTissueTestData anyWithSameIdentifier(boolean anyWithSameIdentifier) {
            this.anyWithSameIdentifier = anyWithSameIdentifier;
            return this;
        }

        public ValidateTissueTestData existing(boolean existing) {
            this.existing = existing;
            return this;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("externalName", externalName)
                    .add("replicate", replicate)
                    .add("donorName", donorName)
                    .add("tissueTypeName", tissueTypeName)
                    .add("slCode", slCode)
                    .add("mediumName", mediumName)
                    .add("highestSection", highestSection)
                    .add("fixativeName", fixativeName)
                    .add("anySimilarInDatabase", anySimilarInDatabase)
                    .add("anyWithSameIdentifier", anyWithSameIdentifier)
                    .add("existing", existing)
                    .toString();
        }
    }

    private static class ValidateExistingTissueTestData {
        String externalName;
        boolean existing = true;
        String fieldProblem = null;

        static ValidateExistingTissueTestData externalName(String externalName) {
            ValidateExistingTissueTestData td = new ValidateExistingTissueTestData();
            td.externalName = externalName;
            return td;
        }

        ValidateExistingTissueTestData existing(boolean existing) {
            this.existing = existing;
            return this;
        }

        ValidateExistingTissueTestData fieldProblem(String fieldProblem) {
            this.fieldProblem = fieldProblem;
            return this;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("externalName", externalName)
                    .add("existing", existing)
                    .add("fieldProblem", fieldProblem)
                    .omitNullValues()
                    .toString();
        }
    }
}
