package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.FFPEProcessingRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.service.work.WorkService;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.assertProblem;

/**
 * Tests {@link FFPEProcessingServiceImp}
 */
public class TestFFPEProcessingService {
    private LabwareRepo mockLwRepo;
    private OperationCommentRepo mockOpComRepo;
    private OperationTypeRepo mockOpTypeRepo;
    private MediumRepo mockMediumRepo;
    private TissueRepo mockTissueRepo;
    private WorkService mockWorkService;
    private OperationService mockOpService;
    private CommentValidationService mockCommentValidationService;
    private LabwareValidatorFactory mockLwValFactory;

    private FFPEProcessingServiceImp service;

    @BeforeEach
    void setup() {
        mockLwRepo = mock(LabwareRepo.class);
        mockOpComRepo = mock(OperationCommentRepo.class);
        mockOpTypeRepo = mock(OperationTypeRepo.class);
        mockMediumRepo = mock(MediumRepo.class);
        mockTissueRepo = mock(TissueRepo.class);
        mockWorkService = mock(WorkService.class);
        mockOpService = mock(OperationService.class);
        mockCommentValidationService = mock(CommentValidationService.class);
        mockLwValFactory = mock(LabwareValidatorFactory.class);

        service = spy(new FFPEProcessingServiceImp(mockLwRepo, mockOpComRepo, mockOpTypeRepo, mockMediumRepo, mockTissueRepo,
                mockWorkService, mockOpService, mockCommentValidationService, mockLwValFactory));
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testPerform(boolean valid) {
        User user = EntityFactory.getUser();
        FFPEProcessingRequest request = new FFPEProcessingRequest("SGP1", List.of("STAN-A1"), 10);
        Work work = new Work(1, "SGP1", null, null, null, null, null, null);
        Comment comment = new Comment(10, "Bananas", "blue");
        List<Labware> labware = List.of(EntityFactory.getTube());
        Medium medium = EntityFactory.getMedium();
        doReturn(medium).when(service).loadMedium(any(), any());
        OperationResult opRes;

        if (valid) {
            when(mockWorkService.validateUsableWork(any(), any())).thenReturn(work);
            doReturn(comment).when(service).loadComment(any(), any());
            doReturn(labware).when(service).loadLabware(any(), any());

            opRes = new OperationResult(makeOps(1), labware);
            doReturn(opRes).when(service).record(any(), any(), any(), any(), any());
        } else {
            when(mockWorkService.validateUsableWork(any(), any())).then(Matchers.addProblem("Bad work number.", work));
            doAnswer(Matchers.addProblem("Bad comment.", comment)).when(service).loadComment(any(), any());
            doAnswer(Matchers.addProblem("Bad labware.", labware)).when(service).loadLabware(any(), any());

            opRes = null;
        }

        if (valid) {
            assertSame(opRes, service.perform(user, request));
        } else {
            Matchers.assertValidationException(() -> service.perform(user, request),
                    "The request could not be validated.", "Bad work number.", "Bad comment.", "Bad labware.");
        }

        verify(mockWorkService).validateUsableWork(anyCollection(), eq(request.getWorkNumber()));
        verify(service).loadMedium(anyCollection(), eq(FFPEProcessingServiceImp.MEDIUM_NAME));
        verify(service).loadComment(anyCollection(), eq(request.getCommentId()));
        verify(service).loadLabware(anyCollection(), eq(request.getBarcodes()));

        if (valid) {
            verify(service).record(user, labware, work, comment, medium);
        } else {
            verify(service, never()).record(any(), any(), any(), any(), any());
        }
    }

    @ParameterizedTest
    @CsvSource({"1, true,", ", false, No comment ID specified.", "404, false, No such comment.", "2, true, Bad comment."})
    public void testLoadComment(Integer commentId, boolean exists, String expectedProblem) {
        Comment comment = (exists ? new Comment(commentId, "Bananas", "blue") : null);
        final List<Integer> receivedCommentIds = new ArrayList<>(1);
        when(mockCommentValidationService.validateCommentIds(any(), any())).then(invocation -> {
            if (expectedProblem!=null) {
                Collection<String> problems = invocation.getArgument(0);
                problems.add(expectedProblem);
            }
            Stream<Integer> idsStream = invocation.getArgument(1);
            idsStream.forEach(receivedCommentIds::add);
            return (comment==null ? List.of() : List.of(comment));
        });

        final List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        assertSame(comment, service.loadComment(problems, commentId));
        if (expectedProblem==null) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactly(expectedProblem);
        }
        if (commentId!=null) {
            verify(mockCommentValidationService).validateCommentIds(any(), any());
            assertThat(receivedCommentIds).containsExactly(commentId);
        }
    }

    @ParameterizedTest
    @CsvSource({"true,", "false, No labware barcodes specified.", "true, Bad labware."})
    public void testLoadLabware(boolean anyBarcodes, String expectedProblem) {
        List<String> problems = new ArrayList<>(expectedProblem==null ? 0 : 1);
        if (!anyBarcodes) {
            assertThat(service.loadLabware(problems, List.of())).isEmpty();
            assertThat(problems).containsExactly(expectedProblem);
            verifyNoInteractions(mockLwValFactory);
            return;
        }

        List<String> barcodes = List.of("STAN-A1");

        LabwareValidator val = mock(LabwareValidator.class);
        when(mockLwValFactory.getValidator()).thenReturn(val);
        List<Labware> labware = List.of(EntityFactory.getTube());
        when(val.loadLabware(any(), any())).thenReturn(labware);
        final List<String> expectedProblems = (expectedProblem == null ? List.of() : List.of(expectedProblem));
        when(val.getErrors()).thenReturn(expectedProblems);

        assertSame(labware, service.loadLabware(problems, barcodes));
        verify(val).loadLabware(mockLwRepo, barcodes);
        verify(val).validateSources();
        assertThat(problems).containsExactlyElementsOf(expectedProblems);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testLoadMedium(boolean exists) {
        String name = "Sosostris";
        Medium medium = (exists ? new Medium(15, name) : null);
        when(mockMediumRepo.findByName(name)).thenReturn(Optional.ofNullable(medium));
        List<String> problems = new ArrayList<>(exists ? 0 : 1);
        assertSame(medium, service.loadMedium(problems, name));
        assertProblem(problems, exists ? null : "Medium \"Sosostris\" not found in database.");
    }

    @Test
    public void testRecord() {
        User user = EntityFactory.getUser();
        var labware = List.of(EntityFactory.getTube());
        Work work = new Work(1, "SGP1", null, null, null, null, null, null);
        Comment comment = new Comment(10, "Bananas", "blue");
        Medium medium = EntityFactory.getMedium();

        List<Operation> ops = makeOps(1);
        doReturn(ops).when(service).createOps(any(), any());
        doNothing().when(service).recordComments(any(), any());
        doNothing().when(service).updateMedium(any(), any());

        assertEquals(new OperationResult(ops, labware), service.record(user, labware, work, comment, medium));
        verify(service).updateMedium(labware, medium);
        verify(service).createOps(user, labware);
        verify(mockWorkService).link(work, ops);
        verify(service).recordComments(comment, ops);
    }

    @Test
    public void testUpdateTissues() {
        Medium medium = new Medium(1, "Sosostris");
        Medium medium2 = new Medium(2, "Ogg");
        Medium medium3 = new Medium(3, "Custard");
        Donor donor = EntityFactory.getDonor();
        SpatialLocation sl = EntityFactory.getSpatialLocation();
        final List<Tissue> tissues = new ArrayList<>(4);
        IntStream.range(0, 3)
                .mapToObj(i -> EntityFactory.makeTissue(donor, sl))
                .forEach(tissues::add);
        tissues.get(0).setMedium(medium2);
        tissues.get(1).setMedium(medium3);
        tissues.get(2).setMedium(medium);
        Tissue tissue0 = tissues.get(0);
        // Add another representation of the same tissue record, to make sure both get updated
        tissues.add(new Tissue(tissue0.getId(), tissue0.getExternalName(), tissue0.getReplicate(), tissue0.getSpatialLocation(),
                tissue0.getDonor(), tissue0.getMedium(), tissue0.getFixative(), tissue0.getHmdmc(), tissue0.getCollectionDate(),
                tissue0.getParentId()));
        assertEquals(tissue0, tissues.get(3));
        assertEquals(tissue0.hashCode(), tissues.get(3).hashCode());

        BioState bs = EntityFactory.getBioState();
        // Create two different samples for each tissue, to make sure all get updated
        List<Sample> samples = IntStream.range(0,tissues.size()*2)
                .mapToObj(i -> new Sample(10+i, null, tissues.get(i/2), bs))
                .collect(toList());
        LabwareType lt = EntityFactory.makeLabwareType(1,1);
        List<Labware> labware = samples.stream()
                .map(sam -> EntityFactory.makeLabware(lt, sam))
                .collect(toList());

        service.updateMedium(labware, medium);
        for (int i = 0; i < labware.size(); ++i) {
            assertSame(samples.get(i), labware.get(i).getFirstSlot().getSamples().get(0));
            assertEquals(medium, samples.get(i).getTissue().getMedium());
            assertEquals(samples.get(i).getTissue(), tissues.get(i/2));
        }
        verify(mockTissueRepo).saveAll(Set.of(tissues.get(0), tissues.get(1)));
    }

    @Test
    public void testCreateOps() {
        OperationType opType = EntityFactory.makeOperationType("FFPE processing", null);
        when(mockOpTypeRepo.getByName(opType.getName())).thenReturn(opType);
        User user = EntityFactory.getUser();
        LabwareType lt = EntityFactory.getTubeType();
        Sample sample = EntityFactory.getSample();
        List<Labware> labware = IntStream.range(0, 2)
                .mapToObj(i -> EntityFactory.makeLabware(lt, sample))
                .collect(toList());
        List<Operation> ops = makeOps(2);
        when(mockOpService.createOperationInPlace(any(), any(), any(), any(), any())).thenReturn(ops.get(0), ops.get(1));

        assertThat(service.createOps(user, labware)).containsExactlyElementsOf(ops);

        for (Labware lw : labware) {
            verify(mockOpService).createOperationInPlace(opType, user, lw, null, null);
        }
    }

    @Test
    public void testRecordComments() {
        Comment comment = new Comment(10, "Bananas", "blue");
        Sample sample1 = EntityFactory.getSample();
        Sample sample2 = new Sample(sample1.getId()+1, null, sample1.getTissue(), sample1.getBioState());
        LabwareType lt = EntityFactory.getTubeType();
        List<Labware> labware = Stream.of(sample1, sample2)
                .map(sam -> EntityFactory.makeLabware(lt, sam))
                .collect(toList());

        final OperationType opType = EntityFactory.makeOperationType("FFPE processing", null);
        List<Operation> ops = labware.stream()
                .map(lw -> {
                    List<Labware> lwList = List.of(lw);
                    return EntityFactory.makeOpForLabware(opType, lwList, lwList);
                }).collect(toList());

        service.recordComments(comment, ops);

        List<OperationComment> expectedOpComs = IntStream.range(0, 2)
                .mapToObj(i -> {
                    Slot slot = labware.get(i).getFirstSlot();
                    return new OperationComment(null, comment, ops.get(i).getId(),
                            slot.getSamples().get(0).getId(), slot.getId(), null);
                })
                .collect(toList());

        verify(mockOpComRepo).saveAll(expectedOpComs);
    }

    private static List<Operation> makeOps(int number) {
        return IntStream.range(0, number)
                .mapToObj(i -> {
                    Operation op = new Operation();
                    op.setId(100 + i);
                    return op;
                }).collect(toList());
    }

}