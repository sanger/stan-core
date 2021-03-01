package uk.ac.sanger.sccp.stan.service.register;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Streams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.verification.VerificationMode;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.BlockRegisterRequest;
import uk.ac.sanger.sccp.stan.request.RegisterRequest;
import uk.ac.sanger.sccp.stan.service.Validator;
import uk.ac.sanger.sccp.stan.service.register.RegisterValidationImp.StringIntKey;

import java.util.*;
import java.util.function.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link RegisterValidationImp}
 * @author dr6
 */
public class TestRegisterValidation {
    private DonorRepo mockDonorRepo;
    private HmdmcRepo mockHmdmcRepo;
    private TissueTypeRepo mockTtRepo;
    private LabwareTypeRepo mockLtRepo;
    private MouldSizeRepo mockMouldSizeRepo;
    private MediumRepo mockMediumRepo;
    private FixativeRepo mockFixativeRepo;
    private TissueRepo mockTissueRepo;
    private SpeciesRepo mockSpeciesRepo;
    private Validator<String> mockDonorNameValidation;
    private Validator<String> mockExternalNameValidation;

    @BeforeEach
    void setup() {
        mockDonorRepo = mock(DonorRepo.class);
        mockHmdmcRepo = mock(HmdmcRepo.class);
        mockTtRepo = mock(TissueTypeRepo.class);
        mockLtRepo = mock(LabwareTypeRepo.class);
        mockMouldSizeRepo = mock(MouldSizeRepo.class);
        mockMediumRepo = mock(MediumRepo.class);
        mockTissueRepo = mock(TissueRepo.class);
        mockFixativeRepo = mock(FixativeRepo.class);
        mockSpeciesRepo = mock(SpeciesRepo.class);
        //noinspection unchecked
        mockDonorNameValidation = mock(Validator.class);
        //noinspection unchecked
        mockExternalNameValidation = mock(Validator.class);
    }

    private void loadSpecies(final Collection<Species> specieses) {
        when(mockSpeciesRepo.findByName(any())).then(invocation -> {
            String name = invocation.getArgument(0);
            return specieses.stream().filter(sp -> sp.getName().equalsIgnoreCase(name)).findAny();
        });
    }

    private RegisterValidationImp create(RegisterRequest request) {
        return spy(new RegisterValidationImp(request, mockDonorRepo, mockHmdmcRepo, mockTtRepo, mockLtRepo,
                mockMouldSizeRepo, mockMediumRepo, mockFixativeRepo, mockTissueRepo, mockSpeciesRepo, mockDonorNameValidation, mockExternalNameValidation));
    }

    private void stubValidationMethods(RegisterValidationImp validation) {
        doNothing().when(validation).validateDonors();
        doNothing().when(validation).validateHmdmcs();
        doNothing().when(validation).validateSpatialLocations();
        doNothing().when(validation).validateLabwareTypes();
        doNothing().when(validation).validateMouldSizes();
        doNothing().when(validation).validateMediums();
        doNothing().when(validation).validateTissues();
        doNothing().when(validation).validateFixatives();
    }

    private void verifyValidateMethods(RegisterValidationImp validation, VerificationMode verificationMode) {
        verify(validation, verificationMode).validateDonors();
        verify(validation, verificationMode).validateHmdmcs();
        verify(validation, verificationMode).validateSpatialLocations();
        verify(validation, verificationMode).validateLabwareTypes();
        verify(validation, verificationMode).validateMouldSizes();
        verify(validation, verificationMode).validateMediums();
        verify(validation, verificationMode).validateTissues();
        verify(validation, verificationMode).validateFixatives();
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
    public void testSpatialLocations(List<String> tissueTypeNames, List<Integer> codes,
                                     List<TissueType> knownTissueTypes, List<SpatialLocation> expectedSLs,
                                     List<String> expectedProblems) {
        @SuppressWarnings("UnstableApiUsage")
        RegisterRequest request = new RegisterRequest(
                Streams.zip(tissueTypeNames.stream(), codes.stream(),
                (name, code) -> {
                    BlockRegisterRequest br = new BlockRegisterRequest();
                    br.setTissueType(name);
                    br.setSpatialLocation(code);
                    return br;
                }).collect(toList()));
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

        //noinspection UnstableApiUsage
        RegisterRequest request = new RegisterRequest(
                Streams.zip(givenHmdmcs.stream(), speciesNames.stream(),
                        (hmdmc, species) -> {
                            BlockRegisterRequest br = new BlockRegisterRequest();
                            br.setHmdmc(hmdmc);
                            br.setSpecies(species);
                            return br;
                        })
                        .collect(toList())
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
    @MethodSource("mouldSizeData")
    public void testValidateMouldSizes(List<MouldSize> knownItems, List<String> givenNames,
                                         List<MouldSize> expectedItems, List<String> expectedProblems) {
        when(mockMouldSizeRepo.findByName(any())).then(invocation -> {
            final String arg = invocation.getArgument(0);
            return knownItems.stream().filter(item -> arg.equalsIgnoreCase(item.getName())).findAny();
        });
        testValidateSimpleField(givenNames, expectedItems, expectedProblems,
                RegisterValidationImp::validateMouldSizes, MouldSize::getName, BlockRegisterRequest::setMouldSize,
                v -> v.mouldSizeMap, RegisterValidationImp::getMouldSize);
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
    @MethodSource("tissueData")
    public void testValidateTissues(final List<ValidateTissueTestData> testData, List<String> expectedProblems) {
        when(mockExternalNameValidation.validate(any(), any())).then(invocation -> {
            String name = invocation.getArgument(0);
            Consumer<String> addProblem = invocation.getArgument(1);
            if (name.contains("*")) {
                addProblem.accept("Invalid name: " + name);
                return true;
            }
            return false;
        });
        when(mockTissueRepo.findByExternalName(anyString())).then(invocation -> {
            final String name = invocation.getArgument(0);
            return testData.stream()
                    .filter(td -> td.anyWithSameIdentifier && td.externalName.equalsIgnoreCase(name))
                    .findAny()
                    .map(td -> EntityFactory.getTissue());
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
                    return br;
                })
                .collect(toList())
        );

        RegisterValidationImp validation = create(request);

        doAnswer(invocation -> {
            final String donorName = invocation.getArgument(0);
            final String tissueTypeName = invocation.getArgument(1);
            final int slCode = invocation.getArgument(2);
            final String mediumName = invocation.getArgument(3);
            final String fixativeName = invocation.getArgument(4);
            final int replicate = invocation.getArgument(5);

            return testData.stream()
                    .anyMatch(td -> td.anySimilarInDatabase && td.donorName.equalsIgnoreCase(donorName)
                            && td.tissueTypeName.equalsIgnoreCase(tissueTypeName)
                            && td.slCode==slCode && td.mediumName.equalsIgnoreCase(mediumName)
                            && td.fixativeName.equalsIgnoreCase(fixativeName) && td.replicate==replicate);

        }).when(validation).anySimilarTissuesInDatabase(any(), any(), anyInt(), any(), any(), anyInt());

        validation.validateTissues();
        assertThat(validation.getProblems()).hasSameElementsAs(expectedProblems);
    }

    @Test
    public void testAnySimilarTissuesInDatabase() {
        Tissue tissue = EntityFactory.getTissue();
        final Medium medium = tissue.getMedium();
        final Fixative fixative = tissue.getFixative();
        Donor donor = tissue.getDonor();
        SpatialLocation sl = tissue.getSpatialLocation();
        RegisterValidationImp validation = create(new RegisterRequest());
        String tissueTypeName = sl.getTissueType().getName();
        String donorName = donor.getDonorName();
        validation.donorMap.put(donorName.toUpperCase(), donor);
        int slCode = sl.getCode();
        int replicate = tissue.getReplicate();
        StringIntKey key = new StringIntKey(tissueTypeName, slCode);
        assertEquals(key.toString(), "("+tissueTypeName.toUpperCase()+", "+slCode+")");
        validation.spatialLocationMap.put(key, sl);
        final String mediumName = medium.getName();
        final String fixativeName = fixative.getName();
        validation.mediumMap.put(mediumName.toUpperCase(), medium);
        validation.fixativeMap.put(fixativeName.toUpperCase(), fixative);

        when(mockTissueRepo.findByDonorIdAndSpatialLocationIdAndMediumIdAndFixativeIdAndReplicate(anyInt(), anyInt(), anyInt(), anyInt(), anyInt()))
                .then(invocation -> {
                    int donorId = invocation.getArgument(0);
                    int slId = invocation.getArgument(1);
                    int mediumId = invocation.getArgument(2);
                    int fixativeId = invocation.getArgument(3);
                    int repl = invocation.getArgument(4);
                    if (tissue.getDonor().getId()==donorId
                            && tissue.getSpatialLocation().getId()==slId
                            && tissue.getMedium().getId()==mediumId
                            && tissue.getFixative().getId()==fixativeId
                            && tissue.getReplicate()==repl) {
                        return Optional.of(tissue);
                    }
                    return Optional.empty();
                });

        assertTrue(validation.anySimilarTissuesInDatabase(donorName.toUpperCase(), tissueTypeName.toUpperCase(), slCode,
                mediumName.toUpperCase(), fixativeName.toUpperCase(), replicate));
        assertTrue(validation.anySimilarTissuesInDatabase(donorName.toLowerCase(), tissueTypeName.toLowerCase(), slCode,
                mediumName.toLowerCase(), fixativeName.toLowerCase(), replicate));

        assertFalse(validation.anySimilarTissuesInDatabase(donorName, null, 0, null, null, 0));

        assertFalse(validation.anySimilarTissuesInDatabase("Bananas", tissueTypeName, slCode,
                mediumName, fixativeName, replicate));
        assertFalse(validation.anySimilarTissuesInDatabase(donorName, "Custard", slCode,
                mediumName, fixativeName, replicate));
        assertFalse(validation.anySimilarTissuesInDatabase(donorName, tissueTypeName, slCode+1,
                mediumName, fixativeName, replicate));
        assertFalse(validation.anySimilarTissuesInDatabase(donorName, tissueTypeName, slCode,
                "Rhubarb", fixativeName, replicate));
        assertFalse(validation.anySimilarTissuesInDatabase(donorName, tissueTypeName, slCode,
                mediumName, "Blancmange", replicate));
        assertFalse(validation.anySimilarTissuesInDatabase(donorName, tissueTypeName, slCode,
                mediumName, fixativeName, replicate+1));
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

    private static Stream<Arguments> donorData() {
        Species human = new Species(1, "Human");
        Species hamster = new Species(2, "Hamster");
        List<Species> knownSpecies = List.of(human, hamster);
        Donor dirk = new Donor(1, "Dirk", LifeStage.adult, human);
        Donor jeff = new Donor(2, "Jeff", LifeStage.fetal, hamster);
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
                        List.of(), knownSpecies, List.of(), List.of("Missing donor identifier.", "Missing life stage.")),
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
        tt.setSpatialLocations(List.of(sl0, sl1));
        return Stream.of(
                // Valid:
                Arguments.of(List.of(name, name.toUpperCase(), name.toLowerCase()), List.of(0, 0, 1),
                        List.of(tt), List.of(sl0, sl1), List.of()),

                // Invalid:
                Arguments.of(List.of(name, "Plumbus", "Slime", "Slime"), List.of(1, 2, 3, 4),
                        List.of(tt), List.of(sl1), List.of("Unknown tissue types: [Plumbus, Slime]")),
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

    private static Stream<Arguments> hmdmcData() {
        Hmdmc h0 = new Hmdmc(20000, "20/000");
        Hmdmc h1 = new Hmdmc(20001, "20/001");
        // List<Hmdmc> knownHmdmcs, List<String> givenHmdmcs, List<String> speciesNames,
        // List<Hmdmc> expectedHmdmcs, List<String> expectedProblems
        return Stream.of(
                Arguments.of(List.of(h0, h1), List.of("20/001", "20/000", "20/000", ""), List.of("Human", "Human", "Human", "Hamster"),
                        List.of(h0, h1), List.of()),
                Arguments.of(List.of(h0, h1), List.of("20/001", "20/404", "20/405"), List.of("Human", "Human", "Human"),
                        List.of(h1), List.of("Unknown HMDMC numbers: [20/404, 20/405]")),
                Arguments.of(List.of(h0), Arrays.asList(null, "20/000", null), List.of("Human", "Human", "Human"),
                        List.of(h0), List.of("Missing HMDMC number.")),
                Arguments.of(List.of(h0, h1), List.of("20/000", "20/001"), List.of("Human", "Hamster"),
                        List.of(h0), List.of("Non-human tissue should not have an HMDMC number.")),
                Arguments.of(List.of(h0, h1), List.of("20/000", "20/001", "20/000", "", "", "20/404"),
                        List.of("Human", "Human", "Human", "Human", "Human", "Human"),
                        List.of(h0, h1), List.of("Missing HMDMC number.", "Unknown HMDMC number: [20/404]"))
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

    private static Stream<Arguments> mouldSizeData() {
        MouldSize ms = EntityFactory.getMouldSize();
        return Stream.of(Arguments.of(List.of(ms), List.of(ms.getName()), List.of(ms), List.of()),
                Arguments.of(List.of(), Arrays.asList(null, ""), List.of(), List.of("Missing mould size.")),
                Arguments.of(List.of(ms), List.of(ms.getName(), "Sausage"), List.of(ms), List.of("Unknown mould size: [Sausage]")));
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


    private static Stream<Arguments> tissueData() {
        return Stream.of(
                // No problems
                Arguments.of(List.of(ValidateTissueTestData.externalName("X1").replicate(1),
                        ValidateTissueTestData.externalName("X2").replicate(2)),
                        List.of()),

                // Some problems
                Arguments.of(List.of(ValidateTissueTestData.externalName("X1").replicate(1),
                        ValidateTissueTestData.externalName("X2").replicate(-4)),
                        List.of("Replicate number cannot be negative.")),
                Arguments.of(List.of(ValidateTissueTestData.externalName("X1").replicate(1),
                        ValidateTissueTestData.externalName("X2").replicate(2).highestSection(-2)),
                        List.of("Highest section number cannot be negative.")),
                Arguments.of(List.of(ValidateTissueTestData.externalName(null)),
                        List.of("Missing external identifier.")),
                Arguments.of(List.of(ValidateTissueTestData.externalName("")),
                        List.of("Missing external identifier.")),
                Arguments.of(List.of(ValidateTissueTestData.externalName("Banana*")),
                        List.of("Invalid name: Banana*")),
                Arguments.of(List.of(ValidateTissueTestData.externalName("xyz").replicate(1),
                        ValidateTissueTestData.externalName("Xyz").replicate(2)),
                        List.of("Repeated external identifier: Xyz")),
                Arguments.of(List.of(ValidateTissueTestData.externalName("X1").replicate(1),
                        ValidateTissueTestData.externalName("X2").replicate(2).anyWithSameIdentifier(true)),
                        List.of("There is already tissue in the database with external identifier X2.")),
                Arguments.of(List.of(ValidateTissueTestData.externalName("X1").replicate(1).anyWithSameIdentifier(true),
                        ValidateTissueTestData.externalName("X1").replicate(2).anyWithSameIdentifier(true),
                        ValidateTissueTestData.externalName("X2").replicate(3).anyWithSameIdentifier(true)),
                        List.of("There is already tissue in the database with external identifier X1.",
                                "There is already tissue in the database with external identifier X2.",
                                "Repeated external identifier: X1")),
                Arguments.of(List.of(ValidateTissueTestData.externalName("X1"),
                        ValidateTissueTestData.externalName("X2")),
                        List.of("Repeated combination of fields: {donor=D, medium=M, fixative=F, tissue type=TT, " +
                                "spatial location=2, replicate=1}")),
                Arguments.of(List.of(ValidateTissueTestData.externalName("X1").replicate(1),
                        ValidateTissueTestData.externalName("X2").replicate(2).anySimilarInDatabase(true)),
                        List.of("There is already similar tissue in the database: {donor=D, medium=M, fixative=F, " +
                                "tissue type=TT, spatial location=2, replicate=2}")),

                // Many problems
                Arguments.of(List.of(ValidateTissueTestData.externalName("X1*").replicate(-1).highestSection(-1),
                        ValidateTissueTestData.externalName(null).replicate(2),
                        ValidateTissueTestData.externalName("X1").replicate(3).anySimilarInDatabase(true),
                        ValidateTissueTestData.externalName("X2").replicate(4).anyWithSameIdentifier(true),
                        ValidateTissueTestData.externalName("X3").replicate(5),
                        ValidateTissueTestData.externalName("X4").replicate(5),
                        ValidateTissueTestData.externalName("X4").replicate(6)),
                        List.of("Replicate number cannot be negative.",
                                "Highest section number cannot be negative.",
                                "Missing external identifier.",
                                "Invalid name: X1*",
                                "Repeated external identifier: X4",
                                "There is already tissue in the database with external identifier X2.",
                                "Repeated combination of fields: {donor=D, medium=M, fixative=F, tissue type=TT, " +
                                        "spatial location=2, replicate=5}",
                                "There is already similar tissue in the database: {donor=D, medium=M, fixative=F, " +
                                        "tissue type=TT, spatial location=2, replicate=3}"))
        );
    }

    static class ValidateTissueTestData {
        String externalName;
        int replicate = 1;
        String donorName = "D";
        String tissueTypeName = "TT";
        int slCode = 2;
        String mediumName = "M";
        String fixativeName = "F";
        int highestSection = 0;
        boolean anySimilarInDatabase;
        boolean anyWithSameIdentifier;

        public ValidateTissueTestData(String externalName) {
            this.externalName = externalName;
        }

        public static ValidateTissueTestData externalName(String externalName) {
            return new ValidateTissueTestData(externalName);
        }

        public ValidateTissueTestData replicate(int replicate) {
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
                    .toString();
        }
    }
}
