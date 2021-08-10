package uk.ac.sanger.sccp.stan.service.operation.confirm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.LabwareRepo;
import uk.ac.sanger.sccp.stan.repo.PlanOperationRepo;
import uk.ac.sanger.sccp.stan.request.confirm.*;
import uk.ac.sanger.sccp.stan.request.confirm.ConfirmSectionLabware.AddressCommentId;
import uk.ac.sanger.sccp.stan.service.sas.SasService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests {@link ConfirmSectionValidationServiceImp}
 * @author dr6
 */
public class TestConfirmSectionValidationService {
    private ConfirmSectionValidationServiceImp service;

    @Mock
    LabwareRepo mockLwRepo;
    @Mock
    PlanOperationRepo mockPlanRepo;
    @Mock
    SasService mockSasService;

    OperationType opType;

    @BeforeEach
    void setup() {
        MockitoAnnotations.initMocks(this);
        service = spy(new ConfirmSectionValidationServiceImp(mockLwRepo, mockPlanRepo, mockSasService));
        opType = new OperationType(2, "Section", OperationTypeFlag.SOURCE_IS_BLOCK.bit(), EntityFactory.getBioState());
    }

    @Test
    public void testValidateNoLabware() {
        ConfirmSectionRequest request = new ConfirmSectionRequest();
        var validation = service.validate(request);
        assertThat(validation.getProblems()).hasSize(1).contains("No labware specified in request.");
        verify(service, never()).validateLabware(any(), any());
        verify(service, never()).lookUpPlans(any(), any());
        verify(service, never()).validateOperations(any(), any(), any(), any());
    }

    @ValueSource(booleans={false, true})
    @ParameterizedTest
    public void testValidate(boolean valid) {
        Labware lw = EntityFactory.getTube();
        ConfirmSectionRequest request = new ConfirmSectionRequest(List.of(new ConfirmSectionLabware(lw.getBarcode())),
                "SAS9000");
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw);

        PlanOperation plan = EntityFactory.makePlanForLabware(opType, List.of(), List.of());
        Map<Integer, PlanOperation> planMap = Map.of(lw.getId(), plan);

        doAnswer(invocation -> {
            Collection<String> problems = invocation.getArgument(0);
            if (!valid) {
                problems.add("lw problem");
            }
            return lwMap;
        }).when(service).validateLabware(any(), any());
        doAnswer(invocation -> {
            Collection<String> problems = invocation.getArgument(0);
            if (!valid) {
                problems.add("plan problem");
            }
            return planMap;
        }).when(service).lookUpPlans(any(), any());
        doAnswer(invocation -> {
            Collection<String> problems = invocation.getArgument(0);
            if (!valid) {
                problems.add("op problem");
            }
            return null;
        }).when(service).validateOperations(any(), any(), any(), any());

        var validation = service.validate(request);
        if (valid) {
            assertThat(validation.getProblems()).isNullOrEmpty();
            assertThat(validation.getLwPlans()).isEqualTo(planMap);
            assertThat(validation.getLabware()).isEqualTo(lwMap);
        } else {
            assertThat(validation.getProblems()).containsExactly("lw problem", "plan problem", "op problem");
        }

        verify(mockSasService).validateUsableSas(any(), eq(request.getSasNumber()));
        verify(service).validateLabware(any(), eq(request.getLabware()));
        verify(service).lookUpPlans(any(), eq(lwMap.values()));
        verify(service).validateOperations(any(), eq(request.getLabware()), eq(lwMap), eq(planMap));
    }

    @MethodSource("validateLabwareArgs")
    @ParameterizedTest
    public void testValidateLabware(Object bcObj, Object lwObj, Object problemObj) {
        List<String> bcs = objToList(bcObj);
        List<Labware> lws = objToList(lwObj);
        List<String> expectedProblems = objToList(problemObj);
        when(mockLwRepo.findByBarcode(any())).then(invocation -> {
            String bc = invocation.getArgument(0);
            return lws.stream().filter(lw -> lw.getBarcode().equalsIgnoreCase(bc)).findAny();
        });
        final List<ConfirmSectionLabware> csls = bcs.stream().map(ConfirmSectionLabware::new).collect(toList());
        final Set<String> problems = new HashSet<>();
        UCMap<Labware> map = service.validateLabware(problems, csls);

        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
        assertEquals(map, UCMap.from(lws, Labware::getBarcode));
    }

    static Stream<Arguments> validateLabwareArgs() {
        Sample sample = EntityFactory.getSample();
        LabwareType lt = EntityFactory.getTubeType();
        Labware destroyedLw = EntityFactory.makeLabware(lt, sample);
        destroyedLw.setDestroyed(true);
        Labware releasedLw = EntityFactory.makeLabware(lt, sample);
        releasedLw.setReleased(true);
        Labware discardedLw = EntityFactory.makeLabware(lt, sample);
        discardedLw.setDiscarded(true);
        Labware nonemptyLw = EntityFactory.makeLabware(lt, sample);
        Labware emptyLw = EntityFactory.makeEmptyLabware(lt);

        final String emptyLwBc = emptyLw.getBarcode();
        final String missingMsg = "Missing labware barcode.";
        final String repeatedMsg = "Repeated labware barcode: " + emptyLwBc;
        final String unknownMsg = "Unknown labware barcode: \"Bananas\"";
        final String destroyedMsg = "Labware " + destroyedLw.getBarcode() + " is destroyed.";
        final String discardedMsg = "Labware " + discardedLw.getBarcode() + " is already discarded.";
        final String releasedMsg = "Labware " + releasedLw.getBarcode() + " is released.";
        final String nonemptyMsg = "Labware " + nonemptyLw.getBarcode() + " already has contents.";
        return Stream.of(
                Arguments.of(emptyLwBc, emptyLw, null),

                Arguments.of("", null, missingMsg),
                Arguments.of(List.of(emptyLwBc, emptyLwBc), emptyLw, repeatedMsg),
                Arguments.of("Bananas", null, unknownMsg),
                Arguments.of(destroyedLw.getBarcode(), destroyedLw, destroyedMsg),
                Arguments.of(discardedLw.getBarcode(), discardedLw, discardedMsg),
                Arguments.of(releasedLw.getBarcode(), releasedLw, releasedMsg),
                Arguments.of(nonemptyLw.getBarcode(), nonemptyLw, nonemptyMsg),

                Arguments.of(List.of("Bananas", "", emptyLwBc, emptyLwBc, destroyedLw.getBarcode(), discardedLw.getBarcode(), releasedLw.getBarcode(),
                        nonemptyLw.getBarcode()), List.of(destroyedLw, releasedLw, discardedLw, nonemptyLw, emptyLw),
                        List.of(missingMsg, repeatedMsg, unknownMsg, destroyedMsg, discardedMsg, releasedMsg, nonemptyMsg))
        );
    }

    @MethodSource("lookUpPlansArgs")
    @ParameterizedTest
    public void testLookUpPlans(List<Labware> labware, List<PlanOperation> plans,
                                List<String> expectedProblems, Map<Integer, PlanOperation> expectedMap) {
        Set<Integer> labwareIds = labware.stream().map(Labware::getId).collect(toSet());
        when(mockPlanRepo.findAllByDestinationIdIn(labwareIds)).thenReturn(plans);

        final Set<String> problems = new HashSet<>();
        Map<Integer, PlanOperation> map = service.lookUpPlans(problems, labware);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
        if (expectedMap==null) {
            assertThat(map).isEmpty();
        } else {
            assertEquals(expectedMap, map);
        }
    }

    static Stream<Arguments> lookUpPlansArgs() {
        LabwareType lt = EntityFactory.getTubeType();
        Sample sample = EntityFactory.getSample();
        Labware lw0 = EntityFactory.makeLabware(lt, sample);

        Labware lw1 = EntityFactory.makeLabware(lt, sample);
        lw1.setBarcode("STAN-001");
        Labware lw2 = EntityFactory.makeLabware(lt, sample);
        lw2.setBarcode("STAN-002");
        Labware lw3 = EntityFactory.makeLabware(lt, sample);
        lw3.setBarcode("STAN-003");
        OperationType opType = EntityFactory.makeOperationType("Section", EntityFactory.getBioState(), OperationTypeFlag.SOURCE_IS_BLOCK);
        OperationType opTypeX = EntityFactory.makeOperationType("NotSection", EntityFactory.getBioState());
        PlanOperation plan1 = EntityFactory.makePlanForLabware(opType, List.of(lw0), List.of(lw1, lw2));
        PlanOperation plan2 = EntityFactory.makePlanForLabware(opType, List.of(lw0), List.of(lw3));
        PlanOperation plan3 = EntityFactory.makePlanForLabware(opType, List.of(lw0), List.of(lw2));
        PlanOperation planX = EntityFactory.makePlanForLabware(opTypeX, List.of(lw0), List.of(lw1));
        return Arrays.stream(new Object[][] {
                { List.of(lw1, lw2, lw3), List.of(plan1, plan2), null, Map.of(lw1.getId(), plan1, lw2.getId(), plan1, lw3.getId(), plan2)},
                { lw1, null, "No plan found for labware STAN-001.", null},
                { List.of(lw1, lw2), List.of(plan1, plan3), "Multiple plans found for labware STAN-002.", Map.of(lw1.getId(), plan1)},
                { lw1, planX, "Expected to find a sectioning plan, but found NotSection.", Map.of(lw1.getId(), planX) },
                { List.of(lw1, lw2, lw3), List.of(plan1, plan3),
                  List.of("No plan found for labware STAN-003.", "Multiple plans found for labware STAN-002."), Map.of(lw1.getId(), plan1) },
        }).map(toListArguments(0, 1, 2));
    }

    @Test
    public void testValidateOperations() {
        final Sample sample = EntityFactory.getSample();
        Labware lw1 = EntityFactory.makeLabware(EntityFactory.getTubeType(), sample);
        lw1.setBarcode("STAN-001");
        Labware lw2 = EntityFactory.makeLabware(EntityFactory.getTubeType(), sample);
        lw2.setBarcode("STAN-002");
        final Address A1 = new Address(1,1);
        final ConfirmSectionLabware csl1 = new ConfirmSectionLabware(lw1.getBarcode(), false,
                List.of(new ConfirmSection(A1, sample.getId(), 12)),
                List.of(new AddressCommentId(A1, 4)));
        final ConfirmSectionLabware csl2 = new ConfirmSectionLabware(lw2.getBarcode(), false,
                List.of(new ConfirmSection(A1, sample.getId(), 15)),
                List.of(new AddressCommentId(A1, 5)));
        List<ConfirmSectionLabware> csls = List.of(
                csl1, csl2,
                new ConfirmSectionLabware("STAN-404"),
                new ConfirmSectionLabware("")
        );
        PlanOperation plan = EntityFactory.makePlanForLabware(opType, List.of(lw1), List.of(lw1));
        Map<Integer, PlanOperation> lwPlans = Map.of(lw1.getId(), plan, lw2.getId(), plan);
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw1, lw2);

        Set<String> problems = new HashSet<>();
        final List<Map<Integer, Set<Integer>>> sampleIdMaps = new ArrayList<>();

        doAnswer(invocation -> {
            if (invocation.getArgument(2)==lw1) {
                Collection<String> probs = invocation.getArgument(0);
                probs.add("Comment problem.");
            }
            return null;
        }).when(service).validateCommentAddresses(any(), any(), any(), any());

        doAnswer(invocation -> {
            Map<Integer, Set<Integer>> sampleIdMap = invocation.getArgument(4);
            assertNotNull(sampleIdMap);
            if (sampleIdMaps.isEmpty()) {
                assertThat(sampleIdMap).isEmpty();
            }
            sampleIdMaps.add(sampleIdMap);
            if (invocation.getArgument(2)==lw2) {
                Collection<String> probs = invocation.getArgument(0);
                probs.add("Section problem.");
            }
            return null;
        }).when(service).validateSections(any(), any(), any(), any(), any());

        service.validateOperations(problems, csls, lwMap, lwPlans);

        // Make sure that the same sample id map is passed to both invocations of validateSections
        assertThat(sampleIdMaps).hasSize(2);
        final Map<Integer, Set<Integer>> sampleIdMap = sampleIdMaps.get(0);
        assertSame(sampleIdMap, sampleIdMaps.get(1));

        assertThat(problems).containsExactlyInAnyOrder("Comment problem.", "Section problem.");
        verify(service).validateCommentAddresses(problems, csl1.getAddressComments(), lw1, plan);
        verify(service).validateSections(problems, csl1.getConfirmSections(), lw1, plan, sampleIdMap);
        verify(service).validateCommentAddresses(problems, csl2.getAddressComments(), lw2, plan);
        verify(service).validateSections(problems, csl2.getConfirmSections(), lw2, plan, sampleIdMap);
    }

    @Test
    public void testValidateCommentAddresses() {
        LabwareType lt = EntityFactory.makeLabwareType(2,3);
        Labware lw0 = EntityFactory.getTube();
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        lw.setBarcode("STAN-001");
        final Address A1 = new Address(1,1);
        final Address B3 = new Address(2,3);
        final Address D4 = new Address(4, 4);
        PlanOperation plan = EntityFactory.makePlanForSlots(opType, List.of(lw0.getFirstSlot()), List.of(lw.getSlot(A1)), EntityFactory.getUser());
        List<AddressCommentId> addressCommentIds = List.of(
                new AddressCommentId(A1, 2), new AddressCommentId(null, 3),
                new AddressCommentId(B3, 4), new AddressCommentId(D4, 5)
        );
        final Set<String> problems = new HashSet<>();

        service.validateCommentAddresses(problems, List.of(), lw, plan);
        assertThat(problems).isEmpty();

        service.validateCommentAddresses(problems, addressCommentIds, lw, plan);
        assertThat(problems).containsExactlyInAnyOrder(
                "Comment specified with no address for labware STAN-001.",
                "Invalid address D4 in comments for labware STAN-001.",
                "No planned action recorded for address B3 in labware STAN-001, specified in comments."
        );
    }

    @Test
    public void testValidateSections() {
        LabwareType lt = EntityFactory.makeLabwareType(2,3);
        Labware lw1 = EntityFactory.makeEmptyLabware(lt);
        lw1.setBarcode("STAN-001");
        Labware lw2 = EntityFactory.makeEmptyLabware(lt);
        lw2.setBarcode("STAN-002");
        Sample sampleA = EntityFactory.getSample();
        Tissue tissueB = EntityFactory.makeTissue(EntityFactory.getDonor(), EntityFactory.getSpatialLocation());
        Sample sampleB = new Sample(sampleA.getId()+1, null, tissueB, sampleA.getBioState());

        LabwareType tubeType = EntityFactory.getTubeType();
        Labware lwA = EntityFactory.makeLabware(tubeType, sampleA);
        final Slot slotA = lwA.getFirstSlot();
        slotA.setBlockSampleId(sampleA.getId());
        slotA.setBlockHighestSection(12);
        Labware lwB = EntityFactory.makeLabware(tubeType, sampleB);
        final Slot slotB = lwB.getFirstSlot();
        slotB.setBlockSampleId(sampleA.getId());

        final Address A1 = new Address(1,1);
        final Address B3 = new Address(2,3);

        PlanOperation plan = EntityFactory.makePlanForSlots(opType, List.of(slotA, slotB, slotB, slotA),
                List.of(lw1.getSlot(A1), lw1.getSlot(A1), lw1.getSlot(B3), lw2.getSlot(A1)), EntityFactory.getUser());

        List<ConfirmSection> cons = List.of(
                new ConfirmSection(A1, sampleA.getId(), 13),
                new ConfirmSection(B3, sampleB.getId(), 14)
        );
        Map<Integer, Set<Integer>> sampleIdSections = new HashMap<>();
        sampleIdSections.put(sampleA.getId()-1, new HashSet<>(List.of(10,11)));

        final Set<String> problems = new HashSet<>(List.of("Identifiable problem."));

        doNothing().when(service).validateSection(any(), any(), any(), any(), any(), any());

        service.validateSections(problems, cons, lw1, plan, sampleIdSections);

        assertThat(sampleIdSections).containsExactlyEntriesOf(Map.of(sampleA.getId()-1, Set.of(10,11)));

        Map<Integer, Integer> sampleMaxSection = Map.of(sampleA.getId(), 12);
        Map<Address, Set<Integer>> plannedSampleIds = Map.of(A1, Set.of(sampleA.getId(), sampleB.getId()),
                B3, Set.of(sampleB.getId()));

        verify(service).validateSection(problems, lw1, cons.get(0), plannedSampleIds, sampleMaxSection, sampleIdSections);
        verify(service).validateSection(problems, lw1, cons.get(1), plannedSampleIds, sampleMaxSection, sampleIdSections);
    }

    @MethodSource("validateSectionArgs")
    @ParameterizedTest
    public void testValidateSection(Labware lw, ConfirmSection con, Map<Address, Set<Integer>> plannedSampleIds,
                                Map<Integer, Integer> sampleMaxSection, Map<Integer, Set<Integer>> inputSampleIdSections,
                                List<String> expectedProblems, Map<Integer, Set<Integer>> newSampleIdSections) {
        Set<String> problems = new HashSet<>();
        Map<Integer, Set<Integer>> sampleIdSections = inputSampleIdSections==null ? new HashMap<>() : new HashMap<>(inputSampleIdSections);
        service.validateSection(problems, lw, con, plannedSampleIds, sampleMaxSection, sampleIdSections);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
        assertEquals(combineMaps(inputSampleIdSections, newSampleIdSections), sampleIdSections);
    }

    static Stream<Arguments> validateSectionArgs() {
        LabwareType lt = EntityFactory.makeLabwareType(2,3);
        Sample sampleA = EntityFactory.getSample();
        Tissue tissueB = EntityFactory.makeTissue(EntityFactory.getDonor(), EntityFactory.getSpatialLocation());
        Sample sampleB = new Sample(sampleA.getId()+1, null, tissueB, sampleA.getBioState());

        final Address A1 = new Address(1,1);
        final Address A2 = new Address(1,2);
        final Address B3 = new Address(2,3);
        final Address F8 = new Address(6, 8);
        final int aid = sampleA.getId();
        final int bid = sampleB.getId();

        final Map<Address, Set<Integer>> plannedSampleIds = Map.of(A1, Set.of(aid, bid), B3, Set.of(bid));
        final Map<Integer, Integer> sampleMaxSection = Map.of(aid, 12);

        Labware lw = EntityFactory.makeEmptyLabware(lt);
        lw.setBarcode("STAN-01");
        // Problems to check:
        //  Section number below last known for sample
        return Arrays.stream(new Object[][] {
                { lw, new ConfirmSection(null, aid, 20), plannedSampleIds, sampleMaxSection, null,
                        "Section specified with no address.", null },
                { lw, new ConfirmSection(A1, null, 20), plannedSampleIds, sampleMaxSection, null,
                        "No sample id specified for section.", null },
                { lw, new ConfirmSection(A1, aid, null), plannedSampleIds, sampleMaxSection, null,
                        "No section number specified for section.", null },
                { lw, new ConfirmSection(F8, aid, 20), plannedSampleIds, sampleMaxSection, null,
                        "Invalid address F8 in labware STAN-01 specified for section.", null },
                { lw, new ConfirmSection(A1, aid, -4), plannedSampleIds, sampleMaxSection, null,
                        "Section number cannot be less than zero.", null },
                { lw, new ConfirmSection(A2, bid, 20), plannedSampleIds, sampleMaxSection, null,
                        "Sample id "+bid+" is not expected in address A2 of labware STAN-01.", Map.of(bid, Set.of(20))},
                { lw, new ConfirmSection(B3, aid, 21), plannedSampleIds, sampleMaxSection, null,
                        "Sample id "+aid+" is not expected in address B3 of labware STAN-01.", Map.of(aid, Set.of(21))},
                { lw, new ConfirmSection(A1, aid, 14), plannedSampleIds, sampleMaxSection, Map.of(aid, Set.of(13,14)),
                        "Repeated section: 14 from sample id "+aid+".", null},
                { lw, new ConfirmSection(A1, aid, 8), plannedSampleIds, sampleMaxSection, null,
                        "Section numbers from sample id "+aid+" must be greater than 12.", Map.of(aid, Set.of(8))},
                { lw, new ConfirmSection(A1, aid, 12), plannedSampleIds, sampleMaxSection, Map.of(aid, new HashSet<>(List.of(16))),
                        "Section numbers from sample id "+aid+" must be greater than 12.", Map.of(aid, Set.of(16,12))},

                { lw, new ConfirmSection(A1, aid, 20), plannedSampleIds, sampleMaxSection, Map.of(aid, new HashSet<>(List.of(18, 19))),
                        null, Map.of(aid, Set.of(18,19,20))},
        }).map(toListArguments(5));
    }

    private static Function<Object[], Arguments> toListArguments(int... indexes) {
        return arguments -> {
            for (int index: indexes) {
                arguments[index] = objToList(arguments[index]);
            }
            return Arguments.of(arguments);
        };
    }

    private static <K, V> Map<K, V> combineMaps(Map<K, V> a, Map<K, V> b) {
        if (a==null || a.isEmpty()) {
            if (b==null || b.isEmpty()) {
                return Map.of();
            }
            return b;
        }
        if (b==null || b.isEmpty()) {
            return a;
        }
        Map<K, V> c = new HashMap<>(a);
        c.putAll(b);
        return c;
    }

    @SuppressWarnings("unchecked")
    private static <E> List<E> objToList(Object obj) {
        if (obj instanceof List) {
            return (List<E>) obj;
        }
        if (obj==null) {
            return List.of();
        }
        return (List<E>) List.of(obj);
    }
}
