package uk.ac.sanger.sccp.stan.service.operation.confirm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.confirm.*;
import uk.ac.sanger.sccp.stan.request.confirm.ConfirmOperationLabware.AddressCommentId;
import uk.ac.sanger.sccp.stan.service.OperationService;
import uk.ac.sanger.sccp.stan.service.ValidationException;
import uk.ac.sanger.sccp.stan.service.operation.confirm.ConfirmOperationServiceImp.ConfirmLabwareResult;

import javax.persistence.EntityManager;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.sameElements;

/**
 * Tests {@link ConfirmOperationServiceImp}
 * @author dr6
 */
public class TestConfirmOperationService {
    private ConfirmOperationValidationFactory mockConfirmOperationValidationFactory;
    private EntityManager mockEntityManager;
    private OperationService mockOperationService;
    private LabwareRepo mockLabwareRepo;
    private PlanOperationRepo mockPlanOperationRepo;
    private SlotRepo mockSlotRepo;
    private SampleRepo mockSampleRepo;
    private CommentRepo mockCommentRepo;
    private OperationCommentRepo mockOperationCommentRepo;
    private MeasurementRepo mockMeasurementRepo;

    private ConfirmOperationServiceImp service;

    @BeforeEach
    void setup() {
        mockConfirmOperationValidationFactory = mock(ConfirmOperationValidationFactory.class);
        mockEntityManager = mock(EntityManager.class);
        mockOperationService = mock(OperationService.class);
        mockLabwareRepo = mock(LabwareRepo.class);
        mockPlanOperationRepo = mock(PlanOperationRepo.class);
        mockSlotRepo = mock(SlotRepo.class);
        mockSampleRepo = mock(SampleRepo.class);
        mockCommentRepo = mock(CommentRepo.class);
        mockOperationCommentRepo = mock(OperationCommentRepo.class);
        mockMeasurementRepo = mock(MeasurementRepo.class);

        service = spy(new ConfirmOperationServiceImp(mockConfirmOperationValidationFactory, mockEntityManager,
                mockOperationService, mockLabwareRepo, mockPlanOperationRepo, mockSlotRepo, mockSampleRepo,
                mockCommentRepo, mockOperationCommentRepo, mockMeasurementRepo));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void testConfirmOperation(boolean valid) {
        User user = EntityFactory.getUser();
        ConfirmOperationRequest request = new ConfirmOperationRequest();
        ConfirmOperationValidation mockValidation = mock(ConfirmOperationValidation.class);
        when(mockConfirmOperationValidationFactory.createConfirmOperationValidation(any())).thenReturn(mockValidation);
        List<String> problems = (valid ? List.of() : List.of("Fell to bits."));
        when(mockValidation.validate()).thenReturn(problems);
        ConfirmOperationResult expectedResult = new ConfirmOperationResult();
        doReturn(expectedResult).when(service).recordConfirmation(any(), any());
        if (valid) {
            assertSame(expectedResult, service.confirmOperation(user, request));
        } else {
            try {
                service.confirmOperation(user, request);
                fail("Expected validation exception");
            } catch (ValidationException ve) {
                assertEquals(ve.getProblems(), problems);
            }
        }
        verify(mockConfirmOperationValidationFactory).createConfirmOperationValidation(request);
        verify(mockValidation).validate();
        verify(service, times(valid ? 1 : 0)).recordConfirmation(user, request);
    }

    @Test
    public void testRecordConfirmation() {
        LabwareType lt = EntityFactory.getTubeType();
        Labware source = EntityFactory.makeLabware(lt, EntityFactory.getSample());
        List<Labware> labware = IntStream.range(0,2).mapToObj(i -> EntityFactory.makeEmptyLabware(lt)).collect(toList());
        Map<String, Labware> labwareMap = bcMap(labware);
        OperationType opType = EntityFactory.makeOperationType("Section");
        Map<Integer, PlanOperation> planMap = labware.stream()
                .collect(toMap(Labware::getId, lw -> EntityFactory.makePlanForLabware(opType, List.of(source), List.of(lw))));

        final List<ConfirmOperationLabware> cols = labware.stream()
                .map(lw -> new ConfirmOperationLabware(lw.getBarcode()))
                .collect(toList());
        doReturn(labwareMap).when(service).loadLabware(any());
        doReturn(planMap).when(service).loadPlans(any());

        Operation op = EntityFactory.makeOpForLabware(opType, List.of(source), List.of(labware.get(0)));

        doReturn(new ConfirmLabwareResult(op, labware.get(0))).when(service).performConfirmation(
                same(cols.get(0)), any(), any(), any());
        doReturn(new ConfirmLabwareResult(null, labware.get(1))).when(service).performConfirmation(
                same(cols.get(1)), any(), any(), any());
        doNothing().when(service).recordComments(any(), any(), any());
        User user = EntityFactory.getUser();

        ConfirmOperationResult result = service.recordConfirmation(user, new ConfirmOperationRequest(cols));

        verify(service).loadLabware(labwareMap.keySet());
        verify(service).loadPlans(sameElements(labware));
        for (int i = 0; i < labware.size(); ++i) {
            Labware lw = labware.get(i);
            verify(service).performConfirmation(cols.get(i), lw, planMap.get(lw.getId()), user);
        }
        verify(service).recordComments(cols.get(0), op.getId(), labware.get(0));
        verify(service).recordComments(cols.get(1), null, labware.get(1));

        assertThat(result.getLabware()).hasSameElementsAs(labware);
        assertThat(result.getOperations()).containsOnly(op);

        assertThrows(IllegalArgumentException.class,
                () -> service.recordConfirmation(user, new ConfirmOperationRequest(
                        List.of(new ConfirmOperationLabware("BANANAS"))
                )), "Invalid labware barcode: BANANAS");
        planMap.remove(labware.get(0).getId());
        assertThrows(IllegalArgumentException.class,
                () -> service.recordConfirmation(user, new ConfirmOperationRequest(cols)),
                "No plan found for labware "+labware.get(0).getBarcode());
    }

    @Test
    public void testLoadLabware() {
        List<Labware> labware = List.of(EntityFactory.getTube(), EntityFactory.makeEmptyLabware(EntityFactory.getTubeType()));
        List<String> barcodes = labware.stream().map(Labware::getBarcode).collect(toList());
        when(mockLabwareRepo.findByBarcodeIn(List.of())).thenReturn(List.of());
        when(mockLabwareRepo.findByBarcodeIn(barcodes)).thenReturn(labware);

        assertThat(service.loadLabware(List.of())).isEmpty();
        assertThat(service.loadLabware(barcodes)).isEqualTo(Map.of(barcodes.get(0), labware.get(0), barcodes.get(1), labware.get(1)));
    }

    @Test
    public void testLoadPlans() {
        LabwareType lt = EntityFactory.getTubeType();
        Labware source = EntityFactory.makeLabware(lt, EntityFactory.getSample());
        List<Labware> labware = List.of(EntityFactory.getTube(), EntityFactory.makeEmptyLabware(lt));
        OperationType opType = EntityFactory.makeOperationType("Section");
        List<PlanOperation> plans = labware.stream()
                .map(lw -> EntityFactory.makePlanForLabware(opType, List.of(source), List.of(lw)))
                .collect(toList());
        Set<Integer> labwareIds = labware.stream().map(Labware::getId).collect(toSet());
        when(mockPlanOperationRepo.findAllByDestinationIdIn(Set.of())).thenReturn(List.of());
        when(mockPlanOperationRepo.findAllByDestinationIdIn(labwareIds)).thenReturn(plans);

        assertThat(service.loadPlans(List.of())).isEmpty();
        assertThat(service.loadPlans(labware)).isEqualTo(Map.of(labware.get(0).getId(), plans.get(0), labware.get(1).getId(), plans.get(1)));
    }

    @ParameterizedTest
    @MethodSource("performConfirmationArguments")
    public void testPerformConfirmation(ConfirmOperationLabware col, OperationType opType, LabwareType lt,
                                        List<Address> planDestAddresses, Integer[] planThicknesses,
                                        boolean expectDiscard, Set<Address> expectedActionAddresses,
                                        Map<Address, Integer> expectedThicknesses) {
        User user = EntityFactory.getUser();
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        lw.setBarcode(col.getBarcode());
        Labware source = EntityFactory.makeLabware(EntityFactory.getTubeType(), EntityFactory.getSample());
        List<Slot> sourceSlots = List.of(source.getFirstSlot());
        List<Slot> destSlots = planDestAddresses.stream()
                .map(lw::getSlot)
                .collect(toList());
        PlanOperation plan = EntityFactory.makePlanForSlots(opType, sourceSlots, destSlots, user);
        if (planThicknesses!=null) {
            for (int i = 0; i < planThicknesses.length; ++i) {
                plan.getPlanActions().get(i).setSampleThickness(planThicknesses[i]);
            }
        }
        Operation op = (expectDiscard ? null : EntityFactory.makeOpForSlots(opType, sourceSlots, destSlots, user));
        when(mockSlotRepo.save(any())).then(Matchers.returnArgument());
        when(mockLabwareRepo.save(any())).then(Matchers.returnArgument());
        when(mockOperationService.createOperation(any(), any(), anyList(), any())).thenReturn(op);

        ConfirmLabwareResult result = service.performConfirmation(col, lw, plan, user);
        assertEquals(result.labware, lw);
        assertEquals(expectDiscard, lw.isDiscarded());
        if (expectedActionAddresses==null || expectedActionAddresses.isEmpty()) {
            assertNull(result.operation);
            verifyNoInteractions(mockOperationService);
            verify(mockLabwareRepo).save(lw);
            verifyNoInteractions(mockMeasurementRepo);
        } else {
            ConfirmLabwareResult expectedResult = new ConfirmLabwareResult(op, lw);
            assertEquals(expectedResult.hashCode(), result.hashCode());
            assertEquals(expectedResult, result);
            assert op != null;
            assertSame(op, result.operation);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Action>> actionsCaptor = ArgumentCaptor.forClass(List.class);
            verify(mockOperationService).createOperation(eq(plan.getOperationType()), eq(user), actionsCaptor.capture(), eq(plan.getId()));
            List<Action> actions = actionsCaptor.getValue();
            List<Slot> expectedDestinations = expectedActionAddresses.stream()
                    .map(lw::getSlot)
                    .collect(toList());
            assertThat(actions.stream().map(Action::getDestination)).hasSameElementsAs(expectedDestinations);
            verify(mockEntityManager).refresh(lw);
            expectedDestinations.forEach(destSlot -> verify(mockSlotRepo).save(destSlot));
            if (expectedThicknesses!=null && !expectedThicknesses.isEmpty()) {
                List<Measurement> expectedMeasurements = expectedThicknesses.entrySet().stream()
                        .filter(e -> e.getValue()!=null)
                        .map(e -> {
                            Action action = actions.stream().filter(a -> a.getDestination().getAddress().equals(e.getKey()))
                                    .findAny()
                                    .orElseThrow();
                            return new Measurement(null, "Thickness", e.getValue().toString(),
                                    action.getSample().getId(), op.getId(), action.getDestination().getId());
                        })
                        .collect(toList());
                verify(mockMeasurementRepo).saveAll(Matchers.sameElements(expectedMeasurements));
            } else {
                verifyNoInteractions(mockMeasurementRepo);
            }
        }
    }

    static Stream<Arguments> performConfirmationArguments() {
        OperationType opType = EntityFactory.makeOperationType("Section");
        LabwareType lt = EntityFactory.makeLabwareType(2,2);
        String bc = "STAN-DST";
        List<Address> planDestAddresses = List.of(
                new Address(1,1), new Address(1,2), new Address(2,1)
        );

        List<ConfirmOperationLabware> cols = List.of(
                new ConfirmOperationLabware(bc),
                new ConfirmOperationLabware(bc, true, null, null),
                new ConfirmOperationLabware(bc, false, List.of(new Address(1,1), new Address(1,2), new Address(2,1)), null),
                new ConfirmOperationLabware(bc, false, List.of(new Address(2,1)), null)
        );
        boolean[] expectLabwareDiscarded = {false, true, true, false};
        Integer[][] planThicknesses = {
                {100, null, 150},
                {100, null, 150},
                null,
                {100, null, 150},
        };
        List<Set<Address>> expectedActionAddresses = Arrays.asList(
                Set.of(new Address(1, 1), new Address(1, 2), new Address(2, 1)),
                null,
                null,
                Set.of(new Address(1, 1), new Address(1, 2))
        );
        List<Map<Address, Integer>> expectedThicknesses = Arrays.asList(
                Map.of(new Address(1,1), 100, new Address(2,1), 150),
                null,
                null,
                Map.of(new Address(1,1), 100)
        );
        return IntStream.range(0, cols.size()).mapToObj(
                i -> Arguments.of(cols.get(i), opType, lt, planDestAddresses, planThicknesses[i],
                        expectLabwareDiscarded[i], expectedActionAddresses.get(i), expectedThicknesses.get(i))
        );
    }

    @ParameterizedTest
    @MethodSource("getOrCreateSampleArguments")
    public void testGetOrCreateSample(PlanAction planAction, Slot slot, Sample expectedSample, boolean creates) {
        if (creates) {
            when(mockSampleRepo.save(any())).thenReturn(expectedSample);
        }

        assertSame(expectedSample, service.getOrCreateSample(planAction, slot));
        if (creates) {
            verify(mockSampleRepo).save(new Sample(null, planAction.getNewSection(), planAction.getSample().getTissue()));
        } else {
            verifyNoInteractions(mockSampleRepo);
        }
    }

    static Stream<Arguments> getOrCreateSampleArguments() {
        Tissue tissue1 = EntityFactory.getTissue();
        Tissue tissue2 = EntityFactory.makeTissue(EntityFactory.getDonor(), EntityFactory.getSpatialLocation());
        Sample blockSample = new Sample(540, null, tissue1);
        List<Sample> sections = List.of(
                new Sample(541, 1, tissue2),
                new Sample(542, 1, tissue1),
                new Sample(543, 2, tissue1)
        );
        Sample newSection = new Sample(544, 3, tissue1);
        Slot sourceSlot = new Slot(null, null, new Address(1,1), List.of(blockSample), null, null);
        Slot emptySlot = new Slot(null, null, new Address(1,1), List.of(), null, null);
        Slot populousSlot = new Slot(null, null, new Address(1,2), sections, null, null);
        return Stream.of(
                Arguments.of(new PlanAction(null, null, sourceSlot, emptySlot, blockSample, null, null),
                        emptySlot, blockSample, false),
                Arguments.of(new PlanAction(null, null, sourceSlot, emptySlot, blockSample, 3, null),
                        emptySlot, newSection, true),
                Arguments.of(new PlanAction(null, null, sourceSlot, populousSlot, blockSample, 3, null),
                        populousSlot, newSection, true),
                Arguments.of(new PlanAction(null, null, sourceSlot, populousSlot, blockSample, 1, null),
                        populousSlot, sections.get(1), false),
                Arguments.of(new PlanAction(null, null, sourceSlot, populousSlot, blockSample, 2, null),
                        populousSlot, sections.get(2), false),
                Arguments.of(new PlanAction(null, null, sourceSlot, populousSlot, sections.get(1), 1, null),
                        populousSlot, sections.get(1), false),
                Arguments.of(new PlanAction(null, null, sourceSlot, emptySlot, sections.get(1), 3, null),
                        emptySlot, newSection, true)
        );
    }

    @ParameterizedTest
    @MethodSource("recordCommentsArguments")
    public void testRecordComments(List<AddressCommentId> addressCommentIds, Integer opId, Labware labware,
                                   List<Comment> comments, List<OperationComment> expectedRecordedComments,
                                   List<Integer> expectedInvalidCommentIds) {
        ConfirmOperationLabware col = new ConfirmOperationLabware(labware.getBarcode());
        col.setAddressComments(addressCommentIds);
        when(mockCommentRepo.findAllByIdIn(any())).thenReturn(comments);
        if (expectedInvalidCommentIds!=null && !expectedInvalidCommentIds.isEmpty()) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.recordComments(col, opId, labware),
                    "Invalid comment ids: "+expectedInvalidCommentIds);
            verifyNoInteractions(mockOperationCommentRepo);
            return;
        }

        service.recordComments(col, opId, labware);
        if (expectedRecordedComments==null || expectedRecordedComments.isEmpty()) {
            verifyNoInteractions(mockCommentRepo);
            verifyNoInteractions(mockOperationCommentRepo);
        } else {
            verify(mockOperationCommentRepo).saveAll(Matchers.sameElements(expectedRecordedComments));
            verify(mockCommentRepo).findAllByIdIn(addressCommentIds.stream().map(AddressCommentId::getCommentId).collect(toSet()));
        }
    }

    static Stream<Arguments> recordCommentsArguments() {
        Sample sample1 = EntityFactory.getSample();
        final Address FIRST = new Address(1,1);
        final Address SECOND = new Address(1,2);
        final Address THIRD = new Address(2,1);
        Sample sample2 = new Sample(sample1.getId()+1, 700, sample1.getTissue());
        final int sam1id = sample1.getId();
        final int sam2id = sample2.getId();
        Labware lw = EntityFactory.makeEmptyLabware(EntityFactory.makeLabwareType(2,2));
        lw.getSlot(FIRST).setSamples(List.of(sample1, sample2));
        lw.getSlot(SECOND).setSamples(List.of(sample1));
        final int slot1id = lw.getSlot(FIRST).getId();
        final int slot2id = lw.getSlot(SECOND).getId();
        final int slot3id = lw.getSlot(THIRD).getId();

        final Comment com1 = new Comment(1, "Comment alpha", "greek");
        final Comment com2 = new Comment(2, "Comment beta", "greek");
        List<Comment> comments = List.of(com1, com2);

        return Stream.of(
                Arguments.of(null, null, lw, null, null, null),
                Arguments.of(List.of(), null, lw, comments, null, null),

                Arguments.of(List.of(new AddressCommentId(FIRST, 1),
                        new AddressCommentId(SECOND, 1),
                        new AddressCommentId(SECOND, 2),
                        new AddressCommentId(THIRD, 1)), 60, lw, comments,
                        List.of(new OperationComment(null, com1, 60, sam1id, slot1id, null),
                                new OperationComment(null, com1, 60, sam2id, slot1id, null),
                                new OperationComment(null, com1, 60, sam1id, slot2id, null),
                                new OperationComment(null, com2, 60, sam1id, slot2id, null),
                                new OperationComment(null, com1, 60, null, slot3id, null)),
                        null),

                Arguments.of(List.of(new AddressCommentId(SECOND, 1),
                        new AddressCommentId(THIRD, 2)), null, lw, comments,
                        List.of(new OperationComment(null, com1, null, sam1id, slot2id, null),
                                new OperationComment(null, com2, null, null, slot3id, null)),
                        null),

                Arguments.of(List.of(new AddressCommentId(SECOND, 1),
                        new AddressCommentId(SECOND, 5),
                        new AddressCommentId(THIRD, 6)), 60, lw, comments,
                        null,
                        List.of(5,6))
        );
    }

    private static Map<String, Labware> bcMap(Collection<Labware> labware) {
        return labware.stream()
                .collect(toMap(Labware::getBarcode, lw -> lw));
    }
}
