package uk.ac.sanger.sccp.stan.service.operation.confirm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.AddressCommentId;
import uk.ac.sanger.sccp.stan.request.confirm.*;
import uk.ac.sanger.sccp.stan.service.*;
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
    private BioRiskService mockBioRiskService;
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
        mockBioRiskService = mock(BioRiskService.class);
        mockOperationService = mock(OperationService.class);
        mockLabwareRepo = mock(LabwareRepo.class);
        mockPlanOperationRepo = mock(PlanOperationRepo.class);
        mockSlotRepo = mock(SlotRepo.class);
        mockSampleRepo = mock(SampleRepo.class);
        mockCommentRepo = mock(CommentRepo.class);
        mockOperationCommentRepo = mock(OperationCommentRepo.class);
        mockMeasurementRepo = mock(MeasurementRepo.class);

        service = spy(new ConfirmOperationServiceImp(mockConfirmOperationValidationFactory, mockEntityManager,
                mockBioRiskService, mockOperationService, mockLabwareRepo, mockPlanOperationRepo, mockSlotRepo,
                mockSampleRepo, mockCommentRepo, mockOperationCommentRepo, mockMeasurementRepo));
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
        OperationType opType = EntityFactory.makeOperationType("Section", null);
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
        verify(service).loadPlans(sameElements(labware, true));
        for (int i = 0; i < labware.size(); ++i) {
            Labware lw = labware.get(i);
            verify(service).performConfirmation(cols.get(i), lw, planMap.get(lw.getId()), user);
        }
        verify(service).recordComments(cols.get(0), op.getId(), labware.get(0));
        verify(service).recordComments(cols.get(1), null, labware.get(1));

        assertThat(result.getLabware()).hasSameElementsAs(labware);
        assertThat(result.getOperations()).containsOnly(op);
        verify(mockBioRiskService).copyOpSampleBioRisks(result.getOperations());

        assertThat(assertThrows(IllegalArgumentException.class,
                () -> service.recordConfirmation(user, new ConfirmOperationRequest(
                        List.of(new ConfirmOperationLabware("BANANAS"))
                )))).hasMessage("Invalid labware barcode: BANANAS");
        planMap.remove(labware.get(0).getId());
        assertThat(assertThrows(IllegalArgumentException.class,
                () -> service.recordConfirmation(user, new ConfirmOperationRequest(cols))))
                .hasMessage("No plan found for labware "+labware.get(0).getBarcode());
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
        OperationType opType = EntityFactory.makeOperationType("Section", null);
        List<PlanOperation> plans = labware.stream()
                .map(lw -> EntityFactory.makePlanForLabware(opType, List.of(source), List.of(lw)))
                .collect(toList());
        Set<Integer> labwareIds = labware.stream().map(Labware::getId).collect(toSet());
        when(mockPlanOperationRepo.findAllByDestinationIdIn(Set.of())).thenReturn(List.of());
        when(mockPlanOperationRepo.findAllByDestinationIdIn(labwareIds)).thenReturn(plans);

        assertThat(service.loadPlans(List.of())).isEmpty();
        assertThat(service.loadPlans(labware)).isEqualTo(Map.of(labware.get(0).getId(), plans.get(0), labware.get(1).getId(), plans.get(1)));
    }

    /** @see ConfirmOperationServiceImp#performConfirmation */
    @ParameterizedTest
    @MethodSource("performConfirmationArgs")
    public void testPerformConfirmation(ConfirmOperationLabware col, OperationType opType, Labware labware,
                                        List<PlanAction> planActions, List<Action> expectedActions,
                                        List<Measurement> expectedMeasurements,
                                        Map<Slot, List<Sample>> expectedContents,
                                        List<Sample> sections) {
        when(mockLabwareRepo.save(any())).then(Matchers.returnArgument());
        final boolean expectDiscard = (expectedActions==null || expectedActions.isEmpty());
        final User user = EntityFactory.getUser();
        final int planId = planActions.get(0).getPlanOperationId();
        final PlanOperation plan = new PlanOperation(planId, opType, null, planActions, user);

        Operation op;
        if (!expectDiscard) {
            when(mockSlotRepo.save(any())).then(Matchers.returnArgument());
            op = new Operation(99, opType, null, null, user);
            when(mockOperationService.createOperation(any(), any(), any(), any())).thenReturn(op);
            doAnswer(invocation -> {
                PlanAction pa = invocation.getArgument(0);
                int section = pa.getNewSection();
                return sections.stream().filter(sec -> sec.getSection()==section)
                        .findAny()
                        .orElseThrow();
            }).when(service).getOrCreateSample(any(), any());
        } else {
            op = null;
        }

        ConfirmLabwareResult result = service.performConfirmation(col, labware, plan, user);

        if (expectDiscard) {
            assertEquals(new ConfirmLabwareResult(null, labware), result);
            assertTrue(labware.isDiscarded());
            verify(mockLabwareRepo).save(labware);
            verifyNoInteractions(mockSlotRepo);
            verifyNoInteractions(mockOperationService);
            verifyNoInteractions(mockMeasurementRepo);
            return;
        }
        assertSame(op, result.operation());
        assertEquals(labware, result.labware());
        assertFalse(labware.isDiscarded());
        verify(mockOperationService).createOperation(opType, user, expectedActions, planId);
        if (expectedMeasurements!=null && !expectedMeasurements.isEmpty()) {
            verify(mockMeasurementRepo).saveAll(Matchers.sameElements(expectedMeasurements, true));
        } else {
            verifyNoInteractions(mockMeasurementRepo);
        }
        verify(mockSlotRepo).saveAll(expectedContents.keySet());
        for (var entry : expectedContents.entrySet()) {
            assertThat(labware.getSlot(entry.getKey().getAddress()).getSamples()).containsExactlyInAnyOrderElementsOf(entry.getValue());
        }
        verify(mockEntityManager).refresh(labware);
    }

    /** @see #testPerformConfirmation */
    static Stream<Arguments> performConfirmationArgs() {
        OperationType opType = EntityFactory.makeOperationType("Section", null);
        final LabwareType lt = EntityFactory.makeLabwareType(1, 2);
        Sample sample = new Sample(50, null, EntityFactory.getTissue(), EntityFactory.getBioState());
        Sample[] sections = {
                null,
                new Sample(51, 1, sample.getTissue(), sample.getBioState()),
                new Sample(52, 2, sample.getTissue(), sample.getBioState()),
                new Sample(53, 3, sample.getTissue(), sample.getBioState()),
        };
        Labware source = EntityFactory.makeLabware(EntityFactory.getTubeType(), sample);
        final Slot srcSlot = source.getFirstSlot();
        Labware[] dests = IntStream.range(0, 4).mapToObj(i -> EntityFactory.makeEmptyLabware(lt)).toArray(Labware[]::new);

        final Address A1 = new Address(1,1);
        final Address A2 = new Address(1,2);

        ConfirmOperationLabware[] cols = {
                new ConfirmOperationLabware(dests[0].getBarcode()),
                new ConfirmOperationLabware(dests[1].getBarcode(), true, null, null),
                new ConfirmOperationLabware(dests[2].getBarcode(), false,
                        List.of(new CancelPlanAction(A1, sample.getId(), 2), new CancelPlanAction(A2, sample.getId(), 3)),
                        null
                ),
                new ConfirmOperationLabware(dests[3].getBarcode(), false,
                        List.of(new CancelPlanAction(A1, sample.getId(), 1),
                                new CancelPlanAction(A1, sample.getId(), 2),
                                new CancelPlanAction(A2, sample.getId(), 3)),
                        null
                )
        };
        Slot otherSlot = EntityFactory.makeEmptyLabware(lt).getFirstSlot();

        List<?>[] planActions = Arrays.stream(dests)
                .map(dest -> List.of(
                        new PlanAction(20, 10, srcSlot, dest.getSlot(A1), sample, 1, 4, null),
                        new PlanAction(21, 10, srcSlot, dest.getSlot(A1), sample, 2, null, null),
                        new PlanAction(22, 10, srcSlot, dest.getSlot(A2), sample, 3, 7, null),
                        new PlanAction(23, 10, srcSlot, otherSlot, sample, 4, null, null)
                ))
                .toArray(List[]::new);

        List<?>[] expectedActions = {
                List.of(new Action(null, null, srcSlot, dests[0].getSlot(A1), sections[1], sample),
                        new Action(null, null, srcSlot, dests[0].getSlot(A1), sections[2], sample),
                        new Action(null, null, srcSlot, dests[0].getSlot(A2), sections[3], sample)),
                null,
                List.of(new Action(null, null, srcSlot, dests[2].getSlot(A1), sections[1], sample)),
                null,
        };

        List<?>[] expectedMeasurements = {
                List.of(
                        new Measurement(null, "Thickness", "4", sections[1].getId(), 99, dests[0].getSlot(A1).getId()),
                        new Measurement(null, "Thickness", "7", sections[3].getId(), 99, dests[0].getSlot(A2).getId())
                ),
                null,
                List.of(
                        new Measurement(null, "Thickness", "4", sections[1].getId(), 99, dests[2].getSlot(A1).getId())
                ),
                null,
        };

        Map<?,?>[] expectedContents = {
                Map.of(dests[0].getSlot(A1), List.of(sections[1], sections[2]), dests[0].getSlot(A2), List.of(sections[3])),
                null,
                Map.of(dests[2].getSlot(A1), List.of(sections[1])),
                null,
        };
        List<Sample> sectionList = Arrays.asList(sections).subList(1, 4);

        return IntStream.range(0, cols.length).mapToObj(i ->
            Arguments.of(cols[i], opType, dests[i], planActions[i], expectedActions[i],
                    expectedMeasurements[i], expectedContents[i], sectionList)
        );
        // nothing cancelled
        // lw cancelled
        // some actions cancelled
        // all actions cancelled
    }

    @ParameterizedTest
    @MethodSource("getOrCreateSampleArguments")
    public void testGetOrCreateSample(PlanAction planAction, Slot slot, Sample expectedSample, boolean creates) {
        if (creates) {
            when(mockSampleRepo.save(any())).thenReturn(expectedSample);
        }

        assertSame(expectedSample, service.getOrCreateSample(planAction, slot));
        if (creates) {
            Integer newSection = planAction.getNewSection();
            if (newSection==null) {
                newSection = planAction.getSample().getSection();
            }
            BioState newBioState = planAction.getNewBioState();
            if (newBioState==null) {
                newBioState = planAction.getSample().getBioState();
            }
            verify(mockSampleRepo).save(makeSample(null, newSection, planAction.getSample().getTissue(), newBioState));
        } else {
            verifyNoInteractions(mockSampleRepo);
        }
    }

    private static Sample makeSample(Integer id, Integer section, Tissue tissue, BioState bioState) {
        return new Sample(id, section, tissue, bioState);
    }

    static Stream<Arguments> getOrCreateSampleArguments() {
        BioState bio = new BioState(1, "Tissue");
        Tissue tissue1 = EntityFactory.getTissue();
        Tissue tissue2 = EntityFactory.makeTissue(EntityFactory.getDonor(), EntityFactory.getSpatialLocation());
        Sample blockSample = makeSample(540, null, tissue1, bio);
        List<Sample> sections = List.of(
                makeSample(541, 1, tissue2, bio),
                makeSample(542, 1, tissue1, bio),
                makeSample(543, 2, tissue1, bio)
        );
        BioState rna = new BioState(2, "RNA");
        Sample newSection = makeSample(544, 3, tissue1, bio);
        Slot sourceSlot = new Slot(null, null, new Address(1,1), List.of(blockSample), null, null);
        Slot emptySlot = new Slot(null, null, new Address(1,1), List.of(), null, null);
        Slot populousSlot = new Slot(null, null, new Address(1,2), sections, null, null);
        return Stream.of(
                Arguments.of(new PlanAction(null, null, sourceSlot, emptySlot, blockSample, null, null, null),
                        emptySlot, blockSample, false),
                Arguments.of(new PlanAction(null, null, sourceSlot, emptySlot, blockSample, 3, null, null),
                        emptySlot, newSection, true),
                Arguments.of(new PlanAction(null, null, sourceSlot, populousSlot, blockSample, 3, null, null),
                        populousSlot, newSection, true),
                Arguments.of(new PlanAction(null, null, sourceSlot, populousSlot, blockSample, 1, null, null),
                        populousSlot, sections.get(1), false),
                Arguments.of(new PlanAction(null, null, sourceSlot, populousSlot, blockSample, 2, null, null),
                        populousSlot, sections.get(2), false),
                Arguments.of(new PlanAction(null, null, sourceSlot, populousSlot, sections.get(1), 1, null, null),
                        populousSlot, sections.get(1), false),
                Arguments.of(new PlanAction(null, null, sourceSlot, emptySlot, sections.get(1), 3, null, null),
                        emptySlot, newSection, true),

                Arguments.of(new PlanAction(null, null, sourceSlot, emptySlot, blockSample, null, null, rna),
                        emptySlot, newSection, true),
                Arguments.of(new PlanAction(null, null, sourceSlot, emptySlot, blockSample, 17, null, rna),
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
            assertThat(assertThrows(IllegalArgumentException.class,
                    () -> service.recordComments(col, opId, labware)))
                    .hasMessage("Invalid comment ids: "+expectedInvalidCommentIds);
            verifyNoInteractions(mockOperationCommentRepo);
            return;
        }

        service.recordComments(col, opId, labware);
        if (expectedRecordedComments==null || expectedRecordedComments.isEmpty()) {
            verifyNoInteractions(mockCommentRepo);
            verifyNoInteractions(mockOperationCommentRepo);
        } else {
            verify(mockOperationCommentRepo).saveAll(Matchers.sameElements(expectedRecordedComments, true));
            verify(mockCommentRepo).findAllByIdIn(addressCommentIds.stream().map(AddressCommentId::getCommentId).collect(toSet()));
        }
    }

    static Stream<Arguments> recordCommentsArguments() {
        Sample sample1 = EntityFactory.getSample();
        final Address FIRST = new Address(1,1);
        final Address SECOND = new Address(1,2);
        final Address THIRD = new Address(2,1);
        Sample sample2 = makeSample(sample1.getId()+1, 700, sample1.getTissue(), EntityFactory.getBioState());
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
