package uk.ac.sanger.sccp.stan.service.operation.confirm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.confirm.*;
import uk.ac.sanger.sccp.stan.request.confirm.ConfirmSectionLabware.AddressCommentId;
import uk.ac.sanger.sccp.stan.service.OperationService;
import uk.ac.sanger.sccp.stan.service.ValidationException;
import uk.ac.sanger.sccp.stan.service.operation.confirm.ConfirmSectionServiceImp.ConfirmLabwareResult;
import uk.ac.sanger.sccp.stan.service.operation.confirm.ConfirmSectionServiceImp.PlanActionKey;
import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import javax.persistence.EntityManager;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests {@link ConfirmSectionServiceImp}
 * @author dr6
 */
public class TestConfirmSectionService {
    private ConfirmSectionValidationService mockValidationService;
    private OperationService mockOpService;
    private LabwareRepo mockLwRepo;
    private SlotRepo mockSlotRepo;
    private MeasurementRepo mockMeasurementRepo;
    private SampleRepo mockSampleRepo;
    private CommentRepo mockCommentRepo;
    private OperationCommentRepo mockOpCommentRepo;
    private EntityManager mockEntityManager;

    private ConfirmSectionServiceImp service;

    @BeforeEach
    void setup() {
        mockValidationService = mock(ConfirmSectionValidationService.class);
        mockOpService = mock(OperationService.class);
        mockLwRepo = mock(LabwareRepo.class);
        mockSlotRepo = mock(SlotRepo.class);
        mockMeasurementRepo = mock(MeasurementRepo.class);
        mockSampleRepo = mock(SampleRepo.class);
        mockCommentRepo = mock(CommentRepo.class);
        mockOpCommentRepo = mock(OperationCommentRepo.class);
        mockEntityManager = mock(EntityManager.class);
        service = spy(new ConfirmSectionServiceImp(mockValidationService, mockOpService, mockLwRepo, mockSlotRepo,
                mockMeasurementRepo, mockSampleRepo, mockCommentRepo, mockOpCommentRepo, mockEntityManager));
    }

    private static OperationType makeOpType() {
        return EntityFactory.makeOperationType("Section", null, OperationTypeFlag.SOURCE_IS_BLOCK);
    }

    @Test
    public void testConfirmOperationInvalid() {
        User user = EntityFactory.getUser();
        ConfirmSectionRequest request = new ConfirmSectionRequest(List.of(new ConfirmSectionLabware("STAN-01")));
        ConfirmSectionValidation validation = new ConfirmSectionValidation(List.of("Everything is bad."));
        doReturn(validation).when(mockValidationService).validate(request);
        ValidationException exception = assertThrows(ValidationException.class, () -> service.confirmOperation(user, request));
        assertEquals(exception.getProblems(), validation.getProblems());
        verify(service, never()).perform(any(), any(), any());
    }

    @Test
    public void testConfirmOperationValid() {
        User user = EntityFactory.getUser();
        Labware lw = EntityFactory.getTube();
        ConfirmSectionRequest request = new ConfirmSectionRequest(List.of(new ConfirmSectionLabware("STAN-01")));
        ConfirmSectionValidation validation = new ConfirmSectionValidation(UCMap.from(Labware::getBarcode, lw), Map.of());
        doReturn(validation).when(mockValidationService).validate(request);
        OperationResult opResult = new OperationResult(List.of(), List.of(lw));
        doReturn(opResult).when(service).perform(user, validation, request);
        assertSame(opResult, service.confirmOperation(user, request));
    }

    @MethodSource("performErrorArgs")
    @ParameterizedTest
    public void testPerformError(ConfirmSectionValidation validation, ConfirmSectionRequest request, String expectedErrorMessage) {
        User user = EntityFactory.getUser();
        assertThat(assertThrows(IllegalArgumentException.class, () -> service.perform(user, validation, request))).hasMessage(expectedErrorMessage);
    }

    static Stream<Arguments> performErrorArgs() {
        OperationType opType = makeOpType();
        Labware lw = EntityFactory.getTube();
        PlanOperation plan = EntityFactory.makePlanForLabware(opType, List.of(lw), List.of(lw));

        return Stream.of(
                Arguments.of(new ConfirmSectionValidation(UCMap.from(Labware::getBarcode, lw), Map.of()),
                        new ConfirmSectionRequest(List.of(new ConfirmSectionLabware(lw.getBarcode()))),
                        "No plan found for labware "+lw.getBarcode()),
                Arguments.of(new ConfirmSectionValidation(new UCMap<>(), Map.of(lw.getId(), plan)),
                        new ConfirmSectionRequest(List.of(new ConfirmSectionLabware(lw.getBarcode()))),
                        "Invalid labware barcode: "+lw.getBarcode())
        );
    }

    @Test
    public void testPerformSuccessful() {
        User user = EntityFactory.getUser();

        LabwareType lt = EntityFactory.makeLabwareType(2,3);
        Labware lw1 = EntityFactory.makeEmptyLabware(lt);
        lw1.setBarcode("STAN-01");
        Labware lw2 = EntityFactory.makeEmptyLabware(lt);
        lw2.setBarcode("STAN-02");

        Labware lw1B = EntityFactory.makeEmptyLabware(lt);
        lw1B.setBarcode("STAN-01");
        Labware lw2B = EntityFactory.makeEmptyLabware(lt);
        lw2B.setBarcode("STAN-02");

        PlanOperation plan1 = new PlanOperation();
        plan1.setId(10);
        PlanOperation plan2 = new PlanOperation();
        plan2.setId(11);

        Operation op1 = new Operation();
        op1.setId(1);
        ConfirmLabwareResult clr1 = new ConfirmLabwareResult(op1, lw1B);
        ConfirmLabwareResult clr2 = new ConfirmLabwareResult(null, lw2B);

        ConfirmSectionLabware csl1 = new ConfirmSectionLabware("STAN-01");
        ConfirmSectionLabware csl2 = new ConfirmSectionLabware("STAN-02");

        doReturn(clr1).when(service).confirmLabware(user, csl1, lw1, plan1);
        doReturn(clr2).when(service).confirmLabware(user, csl2, lw2, plan2);

        doNothing().when(service).recordComments(any(), any(), any());
        doNothing().when(service).updateSourceBlocks(any());

        ConfirmSectionRequest request = new ConfirmSectionRequest(List.of(csl1, csl2));
        ConfirmSectionValidation validation = new ConfirmSectionValidation(UCMap.from(Labware::getBarcode, lw1, lw2),
                Map.of(lw1.getId(), plan1, lw2.getId(), plan2));

        OperationResult result = service.perform(user, validation, request);
        assertThat(result.getLabware()).containsExactly(lw1B, lw2B);
        assertThat(result.getOperations()).containsExactly(op1);

        verify(service).confirmLabware(user, csl1, lw1, plan1);
        verify(service).confirmLabware(user, csl2, lw2, plan2);
        verify(service).recordComments(csl1, op1.getId(), lw1B);
        verify(service).recordComments(csl2, null, lw2B);
        verify(service).updateSourceBlocks(result.getOperations());
    }

    @ValueSource(booleans={false, true})
    @ParameterizedTest
    public void testConfirmLabwareCancelled(boolean markedCancelled) {
        // A labware is still cancelled if its list of confirmed sections is empty,
        // even if the "cancelled" boolean is false.
        Labware lw = EntityFactory.getTube();
        lw.setBarcode("STAN-01");
        ConfirmSectionLabware csl = new ConfirmSectionLabware("STAN-01");
        csl.setCancelled(markedCancelled);

        when(mockLwRepo.save(any())).then(Matchers.returnArgument());

        PlanOperation plan = new PlanOperation();
        User user = EntityFactory.getUser();
        assertEquals(new ConfirmLabwareResult(null, lw), service.confirmLabware(user, csl, lw, plan));

        verify(mockLwRepo).save(lw);
        assertTrue(lw.isDiscarded());

        verify(service, never()).getPlanActionMap(any(), anyInt());
        verify(service, never()).makeAction(any(), any(), any());
        verifyNoInteractions(mockSlotRepo);
        verifyNoInteractions(mockMeasurementRepo);
    }

    @Test
    public void testConfirmLabwareMissingPlanAction() {
        LabwareType lt = EntityFactory.makeLabwareType(2, 3);
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        Sample sample = EntityFactory.getSample();
        lw.setBarcode("STAN-01");
        PlanOperation plan = new PlanOperation();
        Map<PlanActionKey, PlanAction> planActionMap = Map.of();
        doReturn(planActionMap).when(service).getPlanActionMap(any(), anyInt());
        final Address A1 = new Address(1,1);
        ConfirmSectionLabware csl = new ConfirmSectionLabware(lw.getBarcode(), false,
                List.of(new ConfirmSection(A1, sample.getId(), 12)), null);

        assertThat(assertThrows(IllegalArgumentException.class, () -> service.confirmLabware(EntityFactory.getUser(), csl, lw, plan)))
                .hasMessage("No plan action found matching section request: sample id "+sample.getId()+
                        " in STAN-01 slot A1.");
        verifyNoInteractions(mockSlotRepo);
        verifyNoInteractions(mockMeasurementRepo);
    }

    @Test
    public void testConfirmLabware() {
        LabwareType lt = EntityFactory.makeLabwareType(2, 3);
        Labware lw1 = EntityFactory.makeEmptyLabware(lt);
        lw1.setBarcode("STAN-01");
        Sample sample = EntityFactory.getSample();
        Labware lw0 = EntityFactory.makeLabware(EntityFactory.getTubeType(), sample);
        lw0.setBarcode("STAN-00");
        Slot source = lw0.getFirstSlot();
        PlanOperation plan = new PlanOperation();
        plan.setId(1);
        final OperationType opType = makeOpType();
        plan.setOperationType(opType);
        final Address A1 = new Address(1,1);
        final Address B3 = new Address(2,3);
        List<ConfirmSection> csecs = List.of(
                new ConfirmSection(A1, sample.getId(), 10),
                new ConfirmSection(A1, sample.getId(), 11),
                new ConfirmSection(B3, sample.getId(), 12)
        );
        ConfirmSectionLabware csl = new ConfirmSectionLabware(lw1.getBarcode(), false, csecs, List.of());
        Map<PlanActionKey, PlanAction> planActionMap = Stream.of(
                new PlanAction(1, 1, source, lw1.getSlot(A1), sample),
                new PlanAction(2, 1, source, lw1.getSlot(B3), sample, 12, 50, null)
        ).collect(BasicUtils.toMap(PlanActionKey::new, HashMap::new));
        plan.setPlanActions(new ArrayList<>(planActionMap.values()));

        doReturn(planActionMap).when(service).getPlanActionMap(any(), anyInt());

        final List<Sample> sections = new ArrayList<>(3);
        List<Action> actions = csecs.stream().map(csec -> {
            Sample section = new Sample(sample.getId()+1+sections.size(), csec.getNewSection(), sample.getTissue(), sample.getBioState());
            sections.add(section);
            PlanAction pa = planActionMap.get(new PlanActionKey(csec.getDestinationAddress(), csec.getSampleId()));
            Action action = new Action(null, null, pa.getSource(), pa.getDestination(), section, sample);
            doReturn(action).when(service).makeAction(csec, pa, pa.getDestination());
            return action;
        }).collect(toList());

        when(mockSlotRepo.saveAll(any())).then(Matchers.returnArgument());
        Operation op = new Operation();
        op.setId(66);
        when(mockOpService.createOperation(any(), any(), any(), any())).thenReturn(op);
        when(mockMeasurementRepo.saveAll(any())).then(Matchers.returnArgument());

        User user = EntityFactory.getUser();
        ConfirmLabwareResult result = service.confirmLabware(user, csl, lw1, plan);

        assertSame(lw1, result.labware);
        assertSame(op, result.operation);
        ConfirmLabwareResult expectedResult = new ConfirmLabwareResult(op, lw1);
        assertEquals(expectedResult, result);
        assertEquals(expectedResult.hashCode(), result.hashCode());

        verify(service).getPlanActionMap(plan.getPlanActions(), lw1.getId());
        for (ConfirmSection csec : csecs) {
            verify(service).makeAction(csec, planActionMap.get(new PlanActionKey(csec.getDestinationAddress(), csec.getSampleId())), lw1.getSlot(csec.getDestinationAddress()));
        }
        verify(mockSlotRepo).saveAll(Matchers.sameElements(List.of(lw1.getSlot(A1), lw1.getSlot(B3))));
        verify(mockEntityManager).refresh(lw1);
        verify(mockOpService).createOperation(opType, user, actions, plan.getId());
        verify(mockMeasurementRepo).saveAll(List.of(new Measurement(null, "Thickness", "50", sections.get(2).getId(), op.getId(), lw1.getSlot(B3).getId())));
    }

    @Test
    public void testMakeAction() {
        Sample sourceSample = new Sample(100, null, EntityFactory.getTissue(), EntityFactory.getBioState());
        Sample section = new Sample(101, 11, sourceSample.getTissue(), sourceSample.getBioState());
        Labware lw0 = EntityFactory.makeLabware(EntityFactory.getTubeType(), sourceSample);
        Slot source = lw0.getFirstSlot();
        Labware lw1 = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType());
        Slot dest = lw1.getFirstSlot();
        PlanAction pa = new PlanAction(3, 1, source, dest, sourceSample);
        ConfirmSection csec = new ConfirmSection(new Address(1,1), sourceSample.getId(), section.getSection());
        doReturn(section).when(service).getSection(csec, pa, dest);

        Action action = service.makeAction(csec, pa, dest);

        verify(service).getSection(csec, pa, dest);
        assertNull(action.getId());
        assertNull(action.getOperationId());
        assertSame(source, action.getSource());
        assertSame(dest, action.getDestination());
        assertSame(section, action.getSample());
        assertSame(sourceSample, action.getSourceSample());
    }

    @Test
    public void testGetSection() {
        Tissue tissue = EntityFactory.getTissue();
        final BioState bs = EntityFactory.getBioState();
        Sample sourceSample = new Sample(100, null, tissue, bs);
        Labware lw0 = EntityFactory.makeLabware(EntityFactory.getTubeType(), sourceSample);
        Slot source = lw0.getFirstSlot();
        Labware lw1 = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType());
        Slot dest = lw1.getFirstSlot();
        PlanAction pa = new PlanAction(3, 1, source, dest, sourceSample);
        final Integer secNum = 14;
        ConfirmSection csec = new ConfirmSection(new Address(1,1), sourceSample.getId(), secNum);
        Sample otherSection = new Sample(101, 13, tissue, bs);
        Sample newSection = new Sample(102, secNum, tissue, bs);
        dest.getSamples().add(otherSection);
        doReturn(newSection).when(service).createSection(sourceSample, secNum, bs);

        assertSame(newSection, service.getSection(csec, pa, dest));
        verify(service).createSection(sourceSample, secNum, bs);
    }

    @Test
    public void testCreateSection() {
        Sample sourceSample = new Sample(100, null, EntityFactory.getTissue(), EntityFactory.getBioState());
        BioState bs = new BioState(50, "Bananas");
        Integer secNum = 7;
        Sample createdSample = new Sample(101, secNum, sourceSample.getTissue(), bs);
        when(mockSampleRepo.save(any())).thenReturn(createdSample);

        assertSame(createdSample, service.createSection(sourceSample, secNum, bs));
        verify(mockSampleRepo).save(new Sample(null, secNum, sourceSample.getTissue(), bs));
    }

    @Test
    public void testRecordNoComments() {
        ConfirmSectionLabware csl = new ConfirmSectionLabware("STAN-01");
        Labware lw1 = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType());
        service.recordComments(csl, 10, lw1);
        verifyNoInteractions(mockCommentRepo);
        verifyNoInteractions(mockOpCommentRepo);
    }

    @Test
    public void testRecordInvalidCommentId() {
        ConfirmSectionLabware csl = new ConfirmSectionLabware("STAN-01");
        csl.setAddressComments(List.of(new AddressCommentId(new Address(1,1), 404)));
        when(mockCommentRepo.findAllByIdIn(any())).thenReturn(List.of());
        Labware lw1 = EntityFactory.makeEmptyLabware(EntityFactory.getTubeType());
        assertThat(assertThrows(IllegalArgumentException.class, () -> service.recordComments(csl, 10, lw1)))
                .hasMessage("Invalid comment ids: [404]");
        verifyNoInteractions(mockOpCommentRepo);
    }

    @Test
    public void testRecordCommentsValid() {
        LabwareType lt = EntityFactory.makeLabwareType(2,3);
        Labware lw = EntityFactory.makeEmptyLabware(lt);
        final Address A1 = new Address(1,1);
        final Address B3 = new Address(2,3);
        Sample[] samples = IntStream.range(1, 3)
                .mapToObj(i -> new Sample(i, 10+i, EntityFactory.getTissue(), EntityFactory.getBioState()))
                .toArray(Sample[]::new);
        lw.getSlot(A1).getSamples().addAll(Arrays.asList(samples));
        ConfirmSectionLabware csl = new ConfirmSectionLabware(lw.getBarcode(), false, List.of(), List.of(
                new AddressCommentId(A1, 1),
                new AddressCommentId(B3, 1),
                new AddressCommentId(B3, 2)
        ));
        final int opId = 909;
        final Comment comment1 = new Comment(1, "Tastes of pink", "Section");
        final Comment comment2 = new Comment(2, "Looks backwards", "Section");
        List<Comment> comments = List.of(comment1, comment2);
        when(mockCommentRepo.findAllByIdIn(any())).thenReturn(comments);

        service.recordComments(csl, opId, lw);
        verify(mockCommentRepo).findAllByIdIn(Set.of(1,2));
        final int slot1Id = lw.getSlot(A1).getId();
        final int slot2Id = lw.getSlot(B3).getId();
        List<OperationComment> expectedOpComments = List.of(
                new OperationComment(null, comment1, opId, samples[0].getId(), slot1Id, null),
                new OperationComment(null, comment1, opId, samples[1].getId(), slot1Id, null),
                new OperationComment(null, comment1, opId, null, slot2Id, null),
                new OperationComment(null, comment2, opId, null, slot2Id, null)
        );
        verify(mockOpCommentRepo).saveAll(Matchers.sameElements(expectedOpComments));
    }

    @Test
    public void testGetPlanActionMap() {
        Sample[] samples = IntStream.range(1,3)
                .mapToObj(i -> new Sample(i, null, EntityFactory.getTissue(), EntityFactory.getBioState()))
                .toArray(Sample[]::new);
        final Address A1 = new Address(1, 1);
        final Address A2 = new Address(1, 2);
        Slot source = new Slot(10, 1, A1, null, null, null);
        Slot[] slots = {
                new Slot(100, 10, A1, Arrays.asList(samples), null, null),
                new Slot(101, 10, A2, List.of(samples[0]), null, null),
                new Slot(200, 20, A1, Arrays.asList(samples), null, null)
        };
        PlanAction[] pas = {
                new PlanAction(51, 1, source, slots[0], samples[0]),
                new PlanAction(52, 1, source, slots[0], samples[1]),
                new PlanAction(53, 1, source, slots[1], samples[0]),
                new PlanAction(54, 1, source, slots[2], samples[1]),
        };

        Map<PlanActionKey, PlanAction> map = service.getPlanActionMap(Arrays.asList(pas), 10);
        assertEquals(map.size(), 3);
        assertEquals(pas[0], map.get(new PlanActionKey(A1, samples[0].getId())));
        assertEquals(pas[1], map.get(new PlanActionKey(A1, samples[1].getId())));
        assertEquals(pas[2], map.get(new PlanActionKey(A2, samples[0].getId())));
    }

    /**
     * This is a little complicated.
     * I want to make sure that if the operations contain the source slots as separate objects,
     * that the highest new section will still be written for each source slot record.
     * So part way through this I make multiple identical slot objects.
     */
    @Test
    public void testUpdateSourceBlocks() {
        LabwareType tubeType = EntityFactory.getTubeType();
        LabwareType slideType = EntityFactory.makeLabwareType(4,1);
        Sample[] sourceSamples = new Sample[2];
        Labware[] sourceLabware = new Labware[2];
        final BioState bs = EntityFactory.getBioState();
        final Tissue tissue = EntityFactory.getTissue();
        for (int i = 0; i < sourceSamples.length; ++i) {
            final int sampleId = 50 + i;
            sourceSamples[i] = new Sample(sampleId, null, tissue, bs);
            sourceLabware[i] = EntityFactory.makeLabware(tubeType, sourceSamples[i]);
            final Slot slot = sourceLabware[i].getFirstSlot();
            slot.setBlockSampleId(sampleId);
            slot.setBlockHighestSection(10*i);
        }
        Sample[] sections = new Sample[4];
        Labware[] destLabware = new Labware[2];

        for (int i = 0; i < sections.length; ++i) {
            sections[i] = new Sample(100+i, 20+i, tissue, bs);
        }

        destLabware[0] = EntityFactory.makeLabware(slideType, sections[0], sections[3]);
        destLabware[1] = EntityFactory.makeLabware(slideType, sections[1], sections[2]);

        OperationType opType = EntityFactory.makeOperationType("Section", null);
        Operation[] ops = new Operation[2];
        for (int i = 0; i < ops.length; ++i) {
            ops[i] = new Operation(60+i, opType, null, null, null);
        }

        final Address A1 = new Address(1,1);
        final Address B1 = new Address(2,1);
        final Slot[] srcs = Arrays.stream(sourceLabware).map(Labware::getFirstSlot).toArray(Slot[]::new);

        List<Action> op0Actions = List.of(
                new Action(600, ops[0].getId(), srcs[0], destLabware[0].getSlot(A1), sections[0], sourceSamples[0]),
                new Action(601, ops[0].getId(), srcs[1], destLabware[0].getSlot(B1), sections[3], sourceSamples[1])
        );
        ops[0].setActions(op0Actions);

        // Just to make sure that if source slots have competing instances, they still get updated correctly:
        for (int i = 0; i < srcs.length; ++i) {
            Slot src = srcs[i];
            srcs[i] = new Slot(src.getId(), src.getLabwareId(), src.getAddress(), src.getSamples(), src.getBlockSampleId(), src.getBlockHighestSection());
        }

        List<Action> op1Actions = List.of(
                new Action(610, ops[1].getId(), srcs[0], destLabware[1].getSlot(A1), sections[1], sourceSamples[0]),
                new Action(611, ops[1].getId(), srcs[1], destLabware[1].getSlot(B1), sections[2], sourceSamples[1])
        );
        ops[1].setActions(op1Actions);

        final List<Slot> updatedSources = new ArrayList<>(2);
        when(mockSlotRepo.saveAll(any())).then(invocation -> {
            Collection<Slot> slots = invocation.getArgument(0);
            updatedSources.addAll(slots);

            return slots;
        });

        service.updateSourceBlocks(Arrays.asList(ops));

        verify(mockSlotRepo).saveAll(any());

        updatedSources.sort(Comparator.comparing(Slot::getId));
        final List<Slot> expectedUpdatedSources = List.of(
                new Slot(srcs[0].getId(), srcs[0].getLabwareId(), srcs[0].getAddress(), srcs[0].getSamples(), srcs[0].getBlockSampleId(), 21),
                new Slot(srcs[1].getId(), srcs[1].getLabwareId(), srcs[1].getAddress(), srcs[1].getSamples(), srcs[1].getBlockSampleId(), 23)
        );
        assertEquals(expectedUpdatedSources, updatedSources);
    }
}
