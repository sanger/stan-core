package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.*;
import uk.ac.sanger.sccp.stan.request.OpWithSlotCommentsRequest.LabwareWithSlotCommentsRequest;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test {@link OpWithSlotCommentsServiceImp}
 */
public class TestOpWithSlotCommentsService {
    private WorkService mockWorkService;
    private CommentValidationService mockCommentValidationService;
    private OperationService mockOpService;
    private LabwareValidatorFactory mockLwValidatorFactory;
    private LabwareRepo mockLwRepo;
    private OperationTypeRepo mockOpTypeRepo;
    private OperationCommentRepo mockOpCommentRepo;

    private OpWithSlotCommentsServiceImp service;

    @BeforeEach
    public void setup() {
        mockWorkService = mock(WorkService.class);
        mockCommentValidationService = mock(CommentValidationService.class);
        mockOpService = mock(OperationService.class);
        mockLwValidatorFactory = mock(LabwareValidatorFactory.class);
        mockLwRepo = mock(LabwareRepo.class);
        mockOpTypeRepo = mock(OperationTypeRepo.class);
        mockOpCommentRepo = mock(OperationCommentRepo.class);

        service = spy(new OpWithSlotCommentsServiceImp(mockWorkService, mockCommentValidationService, mockOpService,
                mockLwValidatorFactory, mockLwRepo, mockOpTypeRepo, mockOpCommentRepo));
    }

    @Test
    public void testPerform() {
        User user = EntityFactory.getUser();
        Labware lw = EntityFactory.getTube();
        Comment comment = new Comment(30, "Bad", "Stuff");
        OperationType opType = EntityFactory.makeOperationType("opname", null);
        Work work = new Work(300, "SGP300", null, null, null, null, null, Work.Status.active);
        final List<LabwareWithSlotCommentsRequest> lrs = List.of(new LabwareWithSlotCommentsRequest(lw.getBarcode(),
                List.of(new AddressCommentId(new Address(1, 1), 4))));
        OpWithSlotCommentsRequest request = new OpWithSlotCommentsRequest(opType.getName(), work.getWorkNumber(), lrs);

        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw);
        Map<Integer, Comment> commentMap = Map.of(comment.getId(), comment);

        doReturn(opType).when(service).loadOpType(any(), any());
        when(mockWorkService.validateUsableWork(any(), any())).thenReturn(work);
        doReturn(lwMap).when(service).loadLabware(any(), any());
        doReturn(commentMap).when(service).loadComments(any(), any());
        doNothing().when(service).validateAddresses(any(), any(), any());

        OperationResult opRes = new OperationResult(List.of(), List.of(lw));
        doReturn(opRes).when(service).record(any(), any(), any(), any(), any(), any());

        assertSame(opRes, service.perform(user, request));

        verify(service).loadOpType(any(), eq(request.getOperationType()));
        verify(mockWorkService).validateUsableWork(any(), eq(request.getWorkNumber()));
        verify(service).loadLabware(any(), same(lrs));
        verify(service).validateAddresses(any(), same(lwMap), same(lrs));
        verify(service).loadComments(any(), same(lrs));
        verify(service).record(user, opType, lrs, work, lwMap, commentMap);
    }

    @Test
    public void testPerform_invalid() {
        doAnswer(Matchers.addProblem("Bad op type")).when(service).loadOpType(any(), any());
        doAnswer(Matchers.addProblem("Bad work")).when(mockWorkService).validateUsableWork(any(), any());
        doAnswer(Matchers.addProblem("Bad lw")).when(service).loadLabware(any(), any());
        doAnswer(Matchers.addProblem("Bad address")).when(service).validateAddresses(any(), any(), any());
        doAnswer(Matchers.addProblem("Bad comment")).when(service).loadComments(any(), any());

        User user = EntityFactory.getUser();
        OpWithSlotCommentsRequest request = new OpWithSlotCommentsRequest("opTypeName", null, null);
        var lrs = request.getLabware();
        Matchers.assertValidationException(() -> service.perform(user, request), "The request could not be validated.",
                "Bad op type", "Bad work", "Bad lw", "Bad address", "Bad comment");

        verify(service).loadOpType(any(), eq(request.getOperationType()));
        verify(mockWorkService).validateUsableWork(any(), eq(request.getWorkNumber()));
        verify(service).loadLabware(any(), same(lrs));
        verify(service).validateAddresses(any(), isNull(), same(lrs));
        verify(service).loadComments(any(), same(lrs));

        verify(service, never()).record(any(), any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({"opname, true,",
            "opname, false, Operation opname cannot be recorded in-place.",
            "opname,,Unknown operation type: \"opname\"",
            ",,No operation type specified.",
            "'',,No operation type specified.",
    })
    public void testLoadOpType(String opName, Boolean opInPlace, String expectedProblem) {
        OperationType opType;
        if (opInPlace==null) {
            opType = null;
        } else if (opInPlace) {
            opType = EntityFactory.makeOperationType(opName, null, OperationTypeFlag.IN_PLACE);
        } else {
            opType = EntityFactory.makeOperationType(opName, null);
        }
        doReturn(Optional.ofNullable(opType)).when(mockOpTypeRepo).findByName(opName);

        List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        assertSame(opType, service.loadOpType(problems, opName));

        Matchers.assertProblem(problems, expectedProblem);
        if (opType != null) {
            verify(mockOpTypeRepo).findByName(opName);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testLoadLabware(boolean anyErrors) {
        Labware lw1 = EntityFactory.getTube();
        Labware lw2 = EntityFactory.makeEmptyLabware(lw1.getLabwareType());
        List<LabwareWithSlotCommentsRequest> lrs = Stream.of(lw1, lw2)
                .map(lw -> new LabwareWithSlotCommentsRequest(lw.getBarcode(), List.of()))
                .collect(toList());
        LabwareValidator val = mock(LabwareValidator.class);
        when(mockLwValidatorFactory.getValidator()).thenReturn(val);

        Collection<String> errors = anyErrors ? List.of("Problem 1", "Problem 2") : List.of();

        when(val.getErrors()).thenReturn(errors);
        when(val.getLabware()).thenReturn(List.of(lw1, lw2));

        List<String> problems = new ArrayList<>(errors.size());
        UCMap<Labware> lwMap = service.loadLabware(problems, lrs);

        assertThat(lwMap).hasSize(2).containsValues(lw1, lw2);
        assertThat(problems).containsAnyElementsOf(errors);

        verify(val).loadLabware(mockLwRepo, List.of(lw1.getBarcode(), lw2.getBarcode()));
        verify(val).setUniqueRequired(true);
        verify(val).validateSources();
    }

    @Test
    public void testLoadLabware_none() {
        List<String> problems = new ArrayList<>(1);
        assertThat(service.loadLabware(problems, List.of())).isEmpty();
        assertThat(problems).containsExactly("No labware specified.");
        verifyNoInteractions(mockLwValidatorFactory);
    }

    @Test
    public void testValidateAddresses_valid() {
        Labware lw1 = EntityFactory.getTube();
        Sample sample = lw1.getFirstSlot().getSamples().get(0);
        Labware lw2 = EntityFactory.makeLabware(EntityFactory.makeLabwareType(1,3), sample, sample);
        final Address A1 = new Address(1,1);
        final Address A2 = new Address(1,2);
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw1, lw2);
        List<LabwareWithSlotCommentsRequest> lrs = List.of(
                new LabwareWithSlotCommentsRequest(lw1.getBarcode(), List.of(new AddressCommentId(A1, 1),
                        new AddressCommentId(A1, 2))),
                new LabwareWithSlotCommentsRequest(lw2.getBarcode(), List.of(new AddressCommentId(A1, 1),
                        new AddressCommentId(A2, 1)))
        );
        List<String> problems = new ArrayList<>(0);
        service.validateAddresses(problems, lwMap, lrs);
        assertThat(problems).isEmpty();
    }

    @Test
    public void testValidateAddresses_invalid() {
        Sample sample = EntityFactory.getSample();
        Labware lw1 = EntityFactory.makeLabware(EntityFactory.getTubeType(), sample);
        lw1.setBarcode("STAN-1");
        Labware lw2 = EntityFactory.makeLabware(EntityFactory.makeLabwareType(1,3), sample, sample);
        lw2.setBarcode("STAN-2");
        Labware lw3 = EntityFactory.makeLabware(EntityFactory.getTubeType(), sample);
        lw3.setBarcode("STAN-3");
        final Address A1 = new Address(1,1);
        final Address A2 = new Address(1,2);
        final Address A3 = new Address(1,3);
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw1, lw2, lw3);
        List<LabwareWithSlotCommentsRequest> lrs = List.of(
                new LabwareWithSlotCommentsRequest(lw1.getBarcode(), List.of(new AddressCommentId(A1, 1),
                        new AddressCommentId(A1, 1), new AddressCommentId(A2, 2))),
                new LabwareWithSlotCommentsRequest(lw2.getBarcode(), List.of(new AddressCommentId(A1, 1),
                        new AddressCommentId(A3, 1), new AddressCommentId(null, 1),
                        new AddressCommentId(A1, null))),
                new LabwareWithSlotCommentsRequest(lw3.getBarcode(), List.of()),
                new LabwareWithSlotCommentsRequest("X-404", List.of())
        );
        List<String> problems = new ArrayList<>(5);
        service.validateAddresses(problems, lwMap, lrs);
        assertThat(problems).containsExactlyInAnyOrder(
                "Labware specified without comments: STAN-3",
                "No such slot as A2 in labware STAN-1.",
                "Slot A3 in labware STAN-2 is empty.",
                "Comment and slot repeated: 1 in slot A1 of STAN-1.",
                "Null given as address with labware STAN-2."
        );
    }

    @Test
    public void testLoadComments() {
        List<LabwareWithSlotCommentsRequest> lrs = List.of(
                new LabwareWithSlotCommentsRequest("STAN-1",
                        List.of(new AddressCommentId(null, 1), new AddressCommentId(null, 2))),
                new LabwareWithSlotCommentsRequest("STAN-2",
                        List.of(new AddressCommentId(null, null), new AddressCommentId(null, 2),
                                new AddressCommentId(null, 3)))
        );

        Comment com1 = new Comment(1, "com1", "alpha");
        Comment com2 = new Comment(2, "com2", "alpha");
        String problem = "Bad comment id";
        when(mockCommentValidationService.validateCommentIds(any(), any())).then(Matchers.addProblem(problem, List.of(com1, com2)));
        List<String> problems = new ArrayList<>(1);
        Map<Integer, Comment> map = service.loadComments(problems, lrs);
        assertThat(map).hasSize(2);
        assertSame(com1, map.get(1));
        assertSame(com2, map.get(2));

        //noinspection unchecked
        ArgumentCaptor<Stream<Integer>> streamCaptor = ArgumentCaptor.forClass(Stream.class);
        verify(mockCommentValidationService).validateCommentIds(any(), streamCaptor.capture());
        Stream<Integer> stream = streamCaptor.getValue();
        assertThat(problems).containsExactly(problem);
        assertThat(stream).containsExactly(1,2,null,2,3);
    }

    @Test
    public void testRecord() {
        User user = EntityFactory.getUser();
        OperationType opType = EntityFactory.makeOperationType("opname", null, OperationTypeFlag.IN_PLACE);
        Work work = new Work(100, "SGP100", null, null, null, null, null, Work.Status.active);
        Sample sample = EntityFactory.getSample();
        LabwareType lt = EntityFactory.getTubeType();
        Labware lw1 = EntityFactory.makeLabware(lt, sample);
        Labware lw2 = EntityFactory.makeLabware(lt, sample);
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw1, lw2);
        Comment com1 = new Comment(1, "com1", "Alpha");
        Comment com2 = new Comment(2, "com2", "Alpha");
        Map<Integer, Comment> commentMap = Map.of(1, com1, 2, com2);
        Operation op1 = EntityFactory.makeOpForLabware(opType, List.of(lw1), List.of(lw1), user);
        Operation op2 = EntityFactory.makeOpForLabware(opType, List.of(lw2), List.of(lw2), user);
        when(mockOpService.createOperationInPlace(any(), any(), same(lw1), isNull(), isNull())).thenReturn(op1);
        when(mockOpService.createOperationInPlace(any(), any(), same(lw2), isNull(), isNull())).thenReturn(op2);
        OperationComment opcom1 = new OperationComment(null, com1, op1.getId(), sample.getId(), lw1.getFirstSlot().getId(), null);
        OperationComment opcom2 = new OperationComment(null, com2, op1.getId(), sample.getId(), lw1.getFirstSlot().getId(), null);
        OperationComment opcom3 = new OperationComment(null, com1, op2.getId(), sample.getId(), lw2.getFirstSlot().getId(), null);

        List<LabwareWithSlotCommentsRequest> lrs = List.of(
                new LabwareWithSlotCommentsRequest(lw1.getBarcode(), List.of(new AddressCommentId(null, 1))),
                new LabwareWithSlotCommentsRequest(lw2.getBarcode(), List.of(new AddressCommentId(null, 2)))
        );

        Stream<OperationComment> opComStream1 = Stream.of(opcom1, opcom2);
        Stream<OperationComment> opComStream2 = Stream.of(opcom3);
        doReturn(opComStream1).when(service).streamOpComs(any(), same(lw1), any(), any());
        doReturn(opComStream2).when(service).streamOpComs(any(), same(lw2), any(), any());

        OperationResult opres = service.record(user, opType, lrs, work, lwMap, commentMap);
        assertThat(opres.getOperations()).containsExactly(op1, op2);
        assertThat(opres.getLabware()).containsExactly(lw1, lw2);

        verify(mockOpService).createOperationInPlace(opType, user, lw1, null, null);
        verify(mockOpService).createOperationInPlace(opType, user, lw2, null, null);
        verify(service).streamOpComs(op1.getId(), lw1, lrs.get(0).getAddressComments(), commentMap);
        verify(service).streamOpComs(op2.getId(), lw2, lrs.get(1).getAddressComments(), commentMap);
        verify(mockOpCommentRepo).saveAll(List.of(opcom1, opcom2, opcom3));
        verify(mockWorkService).link(work, opres.getOperations());
    }

    @Test
    public void testStreamOpComs() {
        Integer opId = 100;
        Sample sam1 = EntityFactory.getSample();
        Sample sam2 = new Sample(sam1.getId()+1, null, sam1.getTissue(), sam1.getBioState());
        Labware lw = EntityFactory.makeLabware(EntityFactory.makeLabwareType(1,2), sam1, sam1);
        lw.getFirstSlot().addSample(sam2);
        Comment com1 = new Comment(101, "com1", "alpha");
        Comment com2 = new Comment(102, "com2", "alpha");
        Map<Integer, Comment> commentMap = Map.of(com1.getId(), com1, com2.getId(), com2);
        final Address A1 = new Address(1,1);
        final Address A2 = new Address(1,2);
        Integer slot1id = lw.getFirstSlot().getId();
        Integer slot2id = lw.getSlot(A2).getId();
        List<AddressCommentId> acs = List.of(
                new AddressCommentId(A1, 101),
                new AddressCommentId(A2, 101),
                new AddressCommentId(A2, 102)
        );
        assertThat(service.streamOpComs(opId, lw, acs, commentMap)).containsExactlyInAnyOrder(
                new OperationComment(null, com1, opId, sam1.getId(), slot1id, null),
                new OperationComment(null, com1, opId, sam2.getId(), slot1id, null),
                new OperationComment(null, com1, opId, sam1.getId(), slot2id, null),
                new OperationComment(null, com2, opId, sam1.getId(), slot2id, null)
        );
    }
}