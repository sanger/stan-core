package uk.ac.sanger.sccp.stan.service.register;

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
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private TissueRepo mockTissueRepo;
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
        //noinspection unchecked
        mockDonorNameValidation = mock(Validator.class);
        //noinspection unchecked
        mockExternalNameValidation = mock(Validator.class);
    }

    private RegisterValidationImp create(RegisterRequest request) {
        return spy(new RegisterValidationImp(request, mockDonorRepo, mockHmdmcRepo, mockTtRepo, mockLtRepo,
                mockMouldSizeRepo, mockMediumRepo, mockTissueRepo, mockDonorNameValidation, mockExternalNameValidation));
    }

    private void stubValidationMethods(RegisterValidationImp validation) {
        doNothing().when(validation).validateDonors();
        doNothing().when(validation).validateHmdmcs();
        doNothing().when(validation).validateSpatialLocations();
        doNothing().when(validation).validateLabwareTypes();
        doNothing().when(validation).validateMouldSizes();
        doNothing().when(validation).validateMediums();
        doNothing().when(validation).validateTissues();
    }

    private void verifyValidateMethods(RegisterValidationImp validation, VerificationMode verificationMode) {
        verify(validation, verificationMode).validateDonors();
        verify(validation, verificationMode).validateHmdmcs();
        verify(validation, verificationMode).validateSpatialLocations();
        verify(validation, verificationMode).validateLabwareTypes();
        verify(validation, verificationMode).validateMouldSizes();
        verify(validation, verificationMode).validateMediums();
        verify(validation, verificationMode).validateTissues();
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
    public void testValidateDonors(List<String> donorNames, List<LifeStage> lifeStages,
                                   List<Donor> knownDonors, List<Donor> expectedDonors,
                                   List<String> expectedProblems) {
        RegisterRequest request = new RegisterRequest(
                Streams.zip(donorNames.stream(), lifeStages.stream(),
                        (name, lifeStage) -> {
                            BlockRegisterRequest br = new BlockRegisterRequest();
                            br.setDonorIdentifier(name);
                            br.setLifeStage(lifeStage);
                            return br;
                        }).collect(toList())
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
        RegisterRequest request = new RegisterRequest(
                Stream.of("Alpha", "Beta", "Gamma*", "Delta*", "Gamma*")
                .map(s -> {
                    BlockRegisterRequest br = new BlockRegisterRequest();
                    br.setDonorIdentifier(s);
                    br.setLifeStage(LifeStage.adult);
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
    public void testValidateHmdmcs(List<Hmdmc> knownHmdmcs, List<String> givenHmdmcs,
                                   List<Hmdmc> expectedHmdmcs, List<String> expectedProblems) {
        when(mockHmdmcRepo.findByHmdmc(any())).then(invocation -> {
            final String arg = invocation.getArgument(0);
            return knownHmdmcs.stream().filter(h -> arg.equalsIgnoreCase(h.getHmdmc())).findAny();
        });
        testValidateSimpleField(givenHmdmcs, expectedHmdmcs, expectedProblems,
                RegisterValidationImp::validateHmdmcs, Hmdmc::getHmdmc, BlockRegisterRequest::setHmdmc,
                v -> v.hmdmcMap, RegisterValidationImp::getHmdmc);
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
    @MethodSource("tissueData")
    public void testValidateTissues(List<String> names, List<Integer> replicates, List<Integer> highestSections,
                                    List<Tissue> knownTissues, List<String> expectedProblems) {
        when(mockExternalNameValidation.validate(any(), any())).then(invocation -> {
            String name = invocation.getArgument(0);
            Consumer<String> addProblem = invocation.getArgument(1);
            if (name.contains("*")) {
                addProblem.accept("Invalid name: " + name);
                return true;
            }
            return false;
        });
        when(mockTissueRepo.findByExternalNameAndReplicate(anyString(), anyInt())).then(invocation -> {
            final String name = invocation.getArgument(0);
            final int replicate = invocation.getArgument(1);
            return knownTissues.stream()
                    .filter(tissue -> tissue.getExternalName().equalsIgnoreCase(name) && tissue.getReplicate()==replicate)
                    .findAny();
        });
        RegisterRequest request = new RegisterRequest(
                IntStream.range(0, names.size())
                        .mapToObj(i -> {
                            BlockRegisterRequest br = new BlockRegisterRequest();
                            br.setExternalIdentifier(names.get(i));
                            br.setHighestSection(highestSections.get(i));
                            br.setReplicateNumber(replicates.get(i));
                            return br;
                        }).collect(toList())
        );
        RegisterValidationImp validation = create(request);
        validation.validateTissues();
        assertThat(validation.getProblems()).hasSameElementsAs(expectedProblems);
    }

    private  <E> void testValidateSimpleField(List<String> givenStrings,
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
        assertEquals(expectedItemMap, mapFunction.apply(validation));
        for (E item : expectedItems) {
            assertEquals(item, getter.apply(validation, stringFn.apply(item)));
        }
    }

    private static Stream<Arguments> donorData() {
        Donor dirk = new Donor(1, "Dirk", LifeStage.adult);
        Donor jeff = new Donor(2, "Jeff", LifeStage.fetal);
        return Stream.of(
                // Valid:
                Arguments.of(List.of("DONOR1", "Donor2"), List.of(LifeStage.adult, LifeStage.fetal),
                        List.of(), List.of(new Donor(null, "DONOR1", LifeStage.adult),
                                new Donor(null, "Donor2", LifeStage.fetal)),
                        List.of()),
                Arguments.of(List.of("Donor1", "DONOR1"), List.of(LifeStage.adult, LifeStage.adult),
                        List.of(), List.of(new Donor(null, "Donor1", LifeStage.adult)),
                        List.of()),
                Arguments.of(List.of("DIRK", "jeff"),
                        List.of(dirk.getLifeStage(), jeff.getLifeStage()),
                        List.of(dirk, jeff), List.of(dirk, jeff),
                        List.of()),

                // Invalid:
                Arguments.of(List.of("Dirk", "jeff"), List.of(LifeStage.adult, LifeStage.paediatric),
                        List.of(dirk, jeff), List.of(dirk, jeff),
                        List.of("Wrong life stage given for existing donor Jeff")),
                Arguments.of(List.of("Donor1", "DONOR1"), List.of(LifeStage.adult, LifeStage.fetal),
                        List.of(), List.of(new Donor(null, "Donor1", LifeStage.adult)),
                        List.of("Multiple different life stages specified for donor Donor1")),
                Arguments.of(Arrays.asList(null, null), Arrays.asList(null, null),
                        List.of(), List.of(), List.of("Missing donor identifier.", "Missing life stage.")),
                Arguments.of(List.of(""), List.of(LifeStage.adult),
                        List.of(), List.of(), List.of("Missing donor identifier.")),
                Arguments.of(List.of("Donor1", "DONOR1", "jeff", "dirk", "", ""),
                        List.of(LifeStage.adult, LifeStage.fetal, LifeStage.paediatric, LifeStage.paediatric, LifeStage.adult, LifeStage.adult),
                        List.of(dirk, jeff), List.of(new Donor(null, "Donor1", LifeStage.adult), dirk, jeff),
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
                        List.of("Missing tissue type.", "Unknown tissue types: [Plumbus]",
                                "Unknown spatial location 5 for tissue type Arm."))
        );
    }

    private static Stream<Arguments> hmdmcData() {
        Hmdmc h0 = new Hmdmc(20000, "20/000");
        Hmdmc h1 = new Hmdmc(20001, "20/001");
        return Stream.of(
                Arguments.of(List.of(h0, h1), List.of("20/001", "20/000", "20/000"), List.of(h0, h1), List.of()),
                Arguments.of(List.of(h0, h1), List.of("20/001", "20/404", "20/405"), List.of(h1), List.of("Unknown HMDMCs: [20/404, 20/405]")),
                Arguments.of(List.of(h0), Arrays.asList(null, "20/000", null), List.of(h0), List.of("Missing HMDMC.")),
                Arguments.of(List.of(h0, h1), List.of("20/000", "20/001", "20/000", "", "", "20/404"), List.of(h0, h1), List.of("Missing HMDMC.", "Unknown HMDMCs: [20/404]"))
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
                Arguments.of(List.of(lt0, lt1), List.of(name0, name1, name0, "", "", "Banana"), List.of(lt0, lt1), List.of("Missing labware type.", "Unknown labware types: [Banana]"))
        );
    }

    private static Stream<Arguments> mouldSizeData() {
        MouldSize ms = EntityFactory.getMouldSize();
        return Stream.of(Arguments.of(List.of(ms), List.of(ms.getName()), List.of(ms), List.of()),
                Arguments.of(List.of(), Arrays.asList(null, ""), List.of(), List.of()),
                Arguments.of(List.of(ms), List.of("", "Sausage"), List.of(), List.of("Unknown mould sizes: [Sausage]")));
    }


    private static Stream<Arguments> mediumData() {
        Medium med = EntityFactory.getMedium();
        return Stream.of(Arguments.of(List.of(med), List.of(med.getName()), List.of(med), List.of()),
                Arguments.of(List.of(), Arrays.asList(null, ""), List.of(), List.of()),
                Arguments.of(List.of(med), List.of("", "Sausage", "Sausage"), List.of(), List.of("Unknown mediums: [Sausage]")));
    }

    private static Stream<Arguments> tissueData() {
        final Tissue tissue = EntityFactory.getTissue();
        final String name = tissue.getExternalName();
        final int repl = tissue.getReplicate();
        return Stream.of(
                Arguments.of(List.of("NewTissue", "NewTissue", name, name),
                        List.of(1, 2, repl + 1, repl + 2), List.of(0,0,1,7), List.of(tissue), List.of()),

                Arguments.of(List.of(name, name), List.of(repl, repl+1), List.of(1,2), List.of(tissue),
                        List.of("Tissue with external identifier "+name+", replicate number "+repl+" already exists.")),

                Arguments.of(List.of(name, name), List.of(7, 7), List.of(1,2), List.of(tissue),
                        List.of("Repeated external identifier and replicate number: "+name+", 7")),

                Arguments.of(List.of("Banana*", "Custard*", name, "Banana*"), List.of(10, 11, 12, 13), List.of(1, 2, 3, 4),
                        List.of(tissue),
                        List.of("Invalid name: Banana*", "Invalid name: Custard*")),

                Arguments.of(Arrays.asList(null, "Bananas", null), List.of(1,2,3), List.of(1,2,3), List.of(tissue),
                        List.of("Missing external identifier.")),

                Arguments.of(List.of("", "Bananas", ""), List.of(1,2,3), List.of(1,2,3), List.of(tissue),
                        List.of("Missing external identifier.")),

                Arguments.of(List.of(name), List.of(-1), List.of(-1), List.of(tissue),
                        List.of("Replicate number cannot be negative.", "Highest section number cannot be negative."))
        );
    }
}
