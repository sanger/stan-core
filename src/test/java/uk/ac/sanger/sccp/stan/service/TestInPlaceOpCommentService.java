package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.BarcodeAndCommentId;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.addProblem;
import static uk.ac.sanger.sccp.stan.Matchers.assertValidationException;

/**
 * Tests {@link InPlaceOpCommentServiceImp}
 */
public class TestInPlaceOpCommentService {
    private OperationTypeRepo mockOpTypeRepo;
    private LabwareRepo mockLwRepo;
    private OperationCommentRepo mockOpComRepo;
    private CommentValidationService mockCommentValidationService;
    private OperationService mockOpService;
    private LabwareValidatorFactory mockLwValFactory;

    private InPlaceOpCommentServiceImp service;

    @BeforeEach
    void setUp() {
        mockOpTypeRepo = mock(OperationTypeRepo.class);
        mockLwRepo = mock(LabwareRepo.class);
        mockOpComRepo = mock(OperationCommentRepo.class);
        mockCommentValidationService = mock(CommentValidationService.class);
        mockOpService = mock(OperationService.class);
        mockLwValFactory = mock(LabwareValidatorFactory.class);

        service = spy(new InPlaceOpCommentServiceImp(mockOpTypeRepo, mockLwRepo, mockOpComRepo,
                mockCommentValidationService, mockOpService, mockLwValFactory));
    }

    private void verifyValidation(String opTypeName, List<BarcodeAndCommentId> bcoms) {
        verify(service).loadOpType(anyCollection(), eq(opTypeName));
        verify(service).loadLabware(anyCollection(), same(bcoms));
        verify(service).loadComments(anyCollection(), same(bcoms));
        verify(service).checkBarcodesAndCommentIds(anyCollection(), same(bcoms));
    }

    @Test
    public void testPerform_valid() {
        OperationType opType = EntityFactory.makeOperationType("Add comment", null, OperationTypeFlag.IN_PLACE);
        doReturn(opType).when(service).loadOpType(any(), any());
        Labware lw = EntityFactory.getTube();
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw);
        doReturn(lwMap).when(service).loadLabware(any(), any());
        Comment comment = new Comment(1, "Custard", "yellow");
        Map<Integer, Comment> commentMap = Map.of(comment.getId(), comment);
        doReturn(commentMap).when(service).loadComments(any(), any());
        doNothing().when(service).checkBarcodesAndCommentIds(any(), any());

        OperationResult opRes = new OperationResult(List.of(), List.of(lw));
        doReturn(opRes).when(service).record(any(), any(), any(), any(), any());

        User user = EntityFactory.getUser();
        String opTypeName = opType.getName();
        List<BarcodeAndCommentId> bcoms = List.of(new BarcodeAndCommentId(lw.getBarcode(), comment.getId()));

        assertSame(opRes, service.perform(user, opTypeName, bcoms));

        verifyValidation(opTypeName, bcoms);
        verify(service).record(user, opType, lwMap, commentMap, bcoms);
    }

    @Test
    public void testPerform_invalid() {
        doAnswer(addProblem("Bad op type")).when(service).loadOpType(any(), any());
        doAnswer(addProblem("Bad lw")).when(service).loadLabware(any(), any());
        doAnswer(addProblem("Bad comment")).when(service).loadComments(any(), any());
        doAnswer(addProblem("Repeated stuff")).when(service).checkBarcodesAndCommentIds(any(), any());

        User user = EntityFactory.getUser();
        String opTypeName = "Foo";
        List<BarcodeAndCommentId> bcoms = List.of(new BarcodeAndCommentId("alpha", 5));

        assertValidationException(() -> service.perform(user, opTypeName, bcoms), "The request could not be validated.",
                "Bad op type", "Bad lw", "Bad comment", "Repeated stuff");

        verifyValidation(opTypeName, bcoms);
        verify(service, never()).record(any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({",false,false,No operation type specified.",
            "Foo,false,false,Operation type not found: \"Foo\"",
            "Foo,true,false,The operation type Foo cannot be recorded in-place.",
            "Foo,true,true,"})
    public void testLoadOpType(String opTypeName, boolean exists, boolean inPlace, String expectedProblem) {
        final OperationType opType;
        if (!exists) {
            opType = null;
        } else if (inPlace) {
            opType = EntityFactory.makeOperationType(opTypeName, null, OperationTypeFlag.IN_PLACE);
        } else {
            opType = EntityFactory.makeOperationType(opTypeName, null);
        }
        when(mockOpTypeRepo.findByName(any())).thenReturn(Optional.ofNullable(opType));

        final List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        assertSame(opType, service.loadOpType(problems, opTypeName));
        if (expectedProblem==null) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactly(expectedProblem);
        }
        if (opTypeName!=null && !opTypeName.isEmpty()) {
            verify(mockOpTypeRepo).findByName(opTypeName);
        } else {
            verifyNoInteractions(mockOpTypeRepo);
        }
    }

    @Test
    public void testLoadLabware_none() {
        final List<String> problems = new ArrayList<>(1);
        assertThat(service.loadLabware(problems, List.of())).isEmpty();
        assertThat(problems).containsExactly("No labware specified.");
        verifyNoInteractions(mockLwValFactory);
    }

    @Test
    public void testLoadLabware_nullOrMissingBarcodes() {
        final List<String> problems = new ArrayList<>(1);
        assertThat(service.loadLabware(problems, List.of(new BarcodeAndCommentId(null, 1), new BarcodeAndCommentId("", 2))))
                .isEmpty();
        assertThat(problems).containsExactly("Missing labware barcode.");
        verifyNoInteractions(mockLwValFactory);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testLoadLabware(boolean valid) {
        final List<String> problems = new ArrayList<>(valid ? 0 : 3);
        List<BarcodeAndCommentId> bcoms;
        if (valid) {
            bcoms = List.of(
                    new BarcodeAndCommentId("STAN-A1", 1), new BarcodeAndCommentId("stan-a1", null),
                    new BarcodeAndCommentId("STAN-A2", null)
            );
        } else {
            bcoms = List.of(
                    new BarcodeAndCommentId(null, 1), new BarcodeAndCommentId("STAN-A1", 2),
                    new BarcodeAndCommentId("stan-a2", 3)
            );
        }
        LabwareValidator val = mock(LabwareValidator.class);
        when(mockLwValFactory.getValidator()).thenReturn(val);
        Labware lw1 = EntityFactory.getTube();
        Labware lw2 = EntityFactory.makeEmptyLabware(lw1.getLabwareType());
        final List<Labware> lwList = List.of(lw1, lw2);
        when(val.loadLabware(any(), any())).thenReturn(lwList);
        when(val.getLabware()).thenReturn(lwList);
        when(val.getErrors()).thenReturn(valid ? List.of() : List.of("Lw problem A", "Lw problem B"));

        var lwMap = service.loadLabware(problems, bcoms);
        assertThat(lwMap).hasSize(lwList.size());
        lwList.forEach(lw -> assertSame(lw, lwMap.get(lw.getBarcode())));
        if (valid) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactlyInAnyOrder("Missing labware barcode.", "Lw problem A", "Lw problem B");
        }

        verify(val).loadLabware(mockLwRepo, Set.of("STAN-A1", "STAN-A2"));
        verify(val).validateSources();
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,true", "true,false"})
    public void testLoadComments(boolean any, boolean anyProblems) {
        if (!any) {
            final List<String> problems = new ArrayList<>(0);
            assertThat(service.loadComments(problems, null)).isEmpty();
            verifyNoInteractions(mockCommentValidationService);
            assertThat(problems).isEmpty();
            return;
        }
        List<BarcodeAndCommentId> bcoms = List.of(new BarcodeAndCommentId(null, null),
                new BarcodeAndCommentId(null, 4),
                new BarcodeAndCommentId(null, 5));
        final List<Comment> comments = List.of(
                new Comment(1, "Custard", "yellow"),
                new Comment(2, "Rhubarb", "purple")
        );
        if (anyProblems) {
            when(mockCommentValidationService.validateCommentIds(any(), any())).then(invocation -> {
                Collection<String> problems = invocation.getArgument(0);
                problems.add("Problem 1");
                problems.add("Problem 2");
                return comments;
            });
        } else {
            when(mockCommentValidationService.validateCommentIds(any(), any())).thenReturn(comments);
        }
        final List<String> problems = new ArrayList<>(anyProblems ? 2 : 0);
        var map = service.loadComments(problems, bcoms);
        assertThat(map).hasSize(2);
        comments.forEach(com -> assertSame(com, map.get(com.getId())));
        if (anyProblems) {
            assertThat(problems).containsExactlyInAnyOrder("Problem 1", "Problem 2");
        } else {
            assertThat(problems).isEmpty();
        }
        //noinspection unchecked
        ArgumentCaptor<Stream<Integer>> streamCaptor = ArgumentCaptor.forClass(Stream.class);
        verify(mockCommentValidationService).validateCommentIds(any(), streamCaptor.capture());
        Stream<Integer> stream = streamCaptor.getValue();
        assertThat(stream.collect(toList())).containsExactlyInAnyOrder(null, 4, 5);
    }

    @Test
    public void testCheckBarcodesAndCommentIds_none() {
        final List<String> problems = new ArrayList<>(0);
        service.checkBarcodesAndCommentIds(problems, null);
        assertThat(problems).isEmpty();
        service.checkBarcodesAndCommentIds(problems, List.of());
        assertThat(problems).isEmpty();
    }

    @Test
    public void testCheckBarcodesAndCommentIds_dupes() {
        final List<String> problems = new ArrayList<>(1);
        service.checkBarcodesAndCommentIds(problems, List.of(
                new BarcodeAndCommentId(null, 1), new BarcodeAndCommentId(null, 1),
                new BarcodeAndCommentId("Alpha", null), new BarcodeAndCommentId("Alpha", null),
                new BarcodeAndCommentId("STAN-A1", 2), new BarcodeAndCommentId("STAN-A1", 3),
                new BarcodeAndCommentId("STAN-A2", 2),
                new BarcodeAndCommentId("stan-a1", 5), new BarcodeAndCommentId("STAN-A1", 5),
                new BarcodeAndCommentId("stan-a2", 6), new BarcodeAndCommentId("stan-a2", 6),
                new BarcodeAndCommentId("STAN-A1", 5)
        ));
        assertThat(problems).containsExactly("Duplicate barcode and comment IDs given: " +
                "[{barcode=\"STAN-A1\", commentId=5}, {barcode=\"STAN-A2\", commentId=6}]");
    }

    @Test
    public void testCheckBarcodesAndCommentIds_valid() {
        final List<String> problems = new ArrayList<>(0);
        service.checkBarcodesAndCommentIds(problems, List.of(
                new BarcodeAndCommentId("STAN-A1", 1), new BarcodeAndCommentId("stan-a2", 1),
                new BarcodeAndCommentId("STAN-A2", 2)
        ));
        assertThat(problems).isEmpty();
    }

    @Test
    public void testRecord() {
        User user = EntityFactory.getUser();
        OperationType opType = EntityFactory.makeOperationType("Foo", null);
        UCMap<Operation> opMap = new UCMap<>(1);
        final Operation op = new Operation();
        final Labware lw = EntityFactory.getTube();
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lw);
        opMap.put("STAN-A1", op);
        Map<Integer, Comment> commentMap = Map.of(1, new Comment());
        OperationResult opRes = new OperationResult(List.of(op), List.of(lw));
        doReturn(opMap).when(service).createOperations(any(), any(), any());
        doReturn(null).when(service).createComments(any(), any(), any());
        doReturn(opRes).when(service).composeResult(any(), any(), any());
        final List<BarcodeAndCommentId> bcoms = List.of(new BarcodeAndCommentId("STAN-A1", 3));

        assertSame(opRes, service.record(user, opType, lwMap, commentMap, bcoms));

        verify(service).createOperations(user, opType, lwMap.values());
        verify(service).createComments(opMap, commentMap, bcoms);
        verify(service).composeResult(bcoms, opMap, lwMap);
    }

    @Test
    public void testCreateOperations() {
        Labware lw1 = EntityFactory.getTube();
        Labware lw2 = EntityFactory.makeEmptyLabware(lw1.getLabwareType());
        Operation op1 = new Operation();
        Operation op2 = new Operation();
        op1.setId(1);
        op2.setId(2);
        User user = EntityFactory.getUser();
        OperationType opType = EntityFactory.makeOperationType("Foo", null);
        List<Labware> lws = List.of(lw1, lw2);
        doReturn(op1, op2).when(mockOpService).createOperationInPlace(any(), any(), any(), any(), any());

        var opMap = service.createOperations(user, opType, lws);

        assertThat(opMap).hasSize(2);
        assertSame(op1, opMap.get(lw1.getBarcode()));
        assertSame(op2, opMap.get(lw2.getBarcode()));
        verify(mockOpService).createOperationInPlace(opType, user, lw1, null, null);
        verify(mockOpService).createOperationInPlace(opType, user, lw2, null, null);
        verifyNoMoreInteractions(mockOpService);
    }

    @Test
    public void testCreateComments() {
        LabwareType lwType = EntityFactory.getTubeType();
        Sample sample = EntityFactory.getSample();
        Labware[] lws = IntStream.range(0,2)
                .mapToObj(i -> EntityFactory.makeLabware(lwType, sample))
                .toArray(Labware[]::new);
        OperationType opType = EntityFactory.makeOperationType("Foo", null, OperationTypeFlag.IN_PLACE);
        Operation[] ops = Arrays.stream(lws)
                .map(lw -> {
                    List<Labware> lwList = List.of(lw);
                    return EntityFactory.makeOpForLabware(opType, lwList, lwList);
                }).toArray(Operation[]::new);
        UCMap<Operation> opMap = new UCMap<>(2);
        IntStream.range(0, lws.length)
                        .forEach(i -> opMap.put(lws[i].getBarcode(), ops[i]));
        Comment com1 = new Comment(1, "Custard", "yellow");
        Comment com2 = new Comment(2, "Rhubarb", "purple");
        List<BarcodeAndCommentId> bcoms = List.of(
                new BarcodeAndCommentId(lws[0].getBarcode(), 1),
                new BarcodeAndCommentId(lws[0].getBarcode(), 2),
                new BarcodeAndCommentId(lws[1].getBarcode(), 1)
        );
        Map<Integer, Comment> commentMap = Map.of(1, com1, 2, com2);

        List<OperationComment> opComs = List.of(new OperationComment());
        when(mockOpComRepo.saveAll(any())).thenReturn(opComs);

        assertSame(opComs, service.createComments(opMap, commentMap, bcoms));

        verify(mockOpComRepo).saveAll(List.of(
                new OperationComment(null, com1, ops[0].getId(), sample.getId(), lws[0].getFirstSlot().getId(), null),
                new OperationComment(null, com2, ops[0].getId(), sample.getId(), lws[0].getFirstSlot().getId(), null),
                new OperationComment(null, com1, ops[1].getId(), sample.getId(), lws[1].getFirstSlot().getId(), null)
        ));
    }

    @Test
    public void testComposeResult() {
        Operation[] ops = IntStream.range(0,2)
                .mapToObj(i -> {
                    Operation op = new Operation();
                    op.setId(10+i);
                    return op;
                }).toArray(Operation[]::new);
        Labware lw = EntityFactory.getTube();
        Labware[] lws = {lw, EntityFactory.makeEmptyLabware(lw.getLabwareType())};
        UCMap<Operation> opMap = new UCMap<>(2);
        IntStream.range(0, lws.length).forEach(i -> opMap.put(lws[i].getBarcode(), ops[i]));
        UCMap<Labware> lwMap = UCMap.from(Labware::getBarcode, lws);
        List<BarcodeAndCommentId> bcoms = List.of(
                new BarcodeAndCommentId(lws[0].getBarcode(), 1),
                new BarcodeAndCommentId(lws[0].getBarcode(), 2),
                new BarcodeAndCommentId(lws[1].getBarcode(), 3),
                new BarcodeAndCommentId(lws[0].getBarcode(), 4)
        );
        var opres = service.composeResult(bcoms, opMap, lwMap);
        assertThat(opres.getOperations()).containsExactly(ops);
        assertThat(opres.getLabware()).containsExactly(lws);
    }
}