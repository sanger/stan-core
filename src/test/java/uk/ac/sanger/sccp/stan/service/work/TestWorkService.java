package uk.ac.sanger.sccp.stan.service.work;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.Work.SampleSlotId;
import uk.ac.sanger.sccp.stan.model.Work.Status;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.WorkWithComment;
import uk.ac.sanger.sccp.utils.BasicUtils;

import javax.persistence.EntityNotFoundException;
import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * Tests {@link WorkServiceImp}
 * @author dr6
 */
public class TestWorkService {
    private WorkServiceImp workService;

    private ProjectRepo mockProjectRepo;
    private CostCodeRepo mockCostCodeRepo;
    private WorkRepo mockWorkRepo;
    private WorkTypeRepo mockWorkTypeRepo;
    private WorkEventService mockWorkEventService;

    @BeforeEach
    void setup() {
        mockProjectRepo = mock(ProjectRepo.class);
        mockCostCodeRepo = mock(CostCodeRepo.class);
        mockWorkRepo = mock(WorkRepo.class);
        mockWorkEventService = mock(WorkEventService.class);
        mockWorkTypeRepo = mock(WorkTypeRepo.class);

        workService = spy(new WorkServiceImp(mockProjectRepo, mockCostCodeRepo, mockWorkTypeRepo, mockWorkRepo, mockWorkEventService));
    }

    @ParameterizedTest
    @CsvSource(value={
            ",,",
            "1,,",
            ",2,",
            "-1,,Number of blocks cannot be a negative number.",
            ",-2,Number of slides cannot be a negative number.",
    })
    public void testCreateWork(Integer numBlocks, Integer numSlides, String expectedErrorMessage) {
        String projectName = "Stargate";
        String code = "S1234";
        String workTypeName = "Drywalling";
        Project project = new Project(10, projectName);
        when(mockProjectRepo.getByName(projectName)).thenReturn(project);
        CostCode cc = new CostCode(20, code);
        when(mockCostCodeRepo.getByCode(code)).thenReturn(cc);
        WorkType workType = new WorkType(30, workTypeName);
        when(mockWorkTypeRepo.getByName(workTypeName)).thenReturn(workType);
        String prefix = "SGP";
        String workNumber = "SGP4000";
        when(mockWorkRepo.createNumber(prefix)).thenReturn(workNumber);
        User user = new User(1, "user1", User.Role.admin);
        when(mockWorkRepo.save(any())).then(Matchers.returnArgument());

        if (expectedErrorMessage==null) {
            Work result = workService.createWork(user, prefix, workTypeName, projectName, code, numBlocks, numSlides);
            verify(workService).checkPrefix(prefix);
            verify(mockWorkRepo).createNumber(prefix);
            verify(mockWorkRepo).save(result);
            verify(mockWorkEventService).recordEvent(user, result, WorkEvent.Type.create, null);
            assertEquals(new Work(null, workNumber, workType, project, cc, Status.unstarted, numBlocks, numSlides), result);
        } else {
            assertThat(assertThrows(IllegalArgumentException.class, () -> workService.createWork(user, prefix, workTypeName, projectName,
                    code, numBlocks, numSlides))).hasMessage(expectedErrorMessage);
            verifyNoInteractions(mockWorkRepo);
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "true,true"})
    public void testUpdateStatus(boolean success, boolean withComment) {
        User user = new User(1, "user1", User.Role.admin);
        String workNumber = "SGP4000";
        final Integer commentId = (withComment ? 99 : null);
        Status newStatus = (withComment ? Status.paused : Status.completed);
        Work work = new Work(10, workNumber, null, null, null, Status.active);
        when(mockWorkRepo.getByWorkNumber(workNumber)).thenReturn(work);
        if (!success) {
            doThrow(IllegalArgumentException.class).when(mockWorkEventService).recordStatusChange(any(), any(), any(), any());
            assertThrows(IllegalArgumentException.class, () -> workService.updateStatus(user, workNumber, newStatus, commentId));
            verify(mockWorkRepo, never()).save(any());
        } else {
            Comment comment = (withComment ? new Comment(commentId, "Custard", "Alabama") : null);
            WorkEvent event = new WorkEvent(10, work, null, null, comment, null);
            when(mockWorkEventService.recordStatusChange(any(), any(), any(), any())).thenReturn(event);
            when(mockWorkRepo.save(any())).then(Matchers.returnArgument());
            WorkWithComment wc = workService.updateStatus(user, workNumber, newStatus, commentId);

            assertSame(work, wc.getWork());
            assertEquals(withComment ? comment.getText() : null, wc.getComment());

            verify(mockWorkRepo).save(work);
            assertEquals(work.getStatus(), newStatus);
        }
    }

    @ParameterizedTest
    @CsvSource(value={
            ",,",
            "1,,",
            ",1,",
            "1,2,",
            "2,1,",
            ",-2,Number of blocks cannot be a negative number.",
            "2,-1,Number of blocks cannot be a negative number.",
    })
    public void testUpdateNumBlocks(Integer oldValue, Integer newValue, String expectedErrorMessage) {
        String workNumber = "SGP4000";
        Work work = new Work(10, workNumber, null, null, null, Status.active);
        work.setNumBlocks(oldValue);
        when(mockWorkRepo.getByWorkNumber(workNumber)).thenReturn(work);
        User user = EntityFactory.getUser();

        if (expectedErrorMessage!=null) {
            assertThat(assertThrows(IllegalArgumentException.class, () -> workService.updateWorkNumBlocks(user, workNumber, newValue)))
                    .hasMessage(expectedErrorMessage);
            verify(mockWorkRepo, never()).save(any());
        } else if (Objects.equals(oldValue, newValue)) {
            assertSame(work, workService.updateWorkNumBlocks(user, workNumber, newValue));
            assertEquals(work.getNumBlocks(), newValue);
            verify(mockWorkRepo, never()).save(any());
        } else {
            when(mockWorkRepo.save(any())).then(Matchers.returnArgument());
            assertSame(work, workService.updateWorkNumBlocks(user, workNumber, newValue));
            verify(mockWorkRepo).save(work);
            assertEquals(work.getNumBlocks(), newValue);
        }
    }

    @ParameterizedTest
    @CsvSource(value={
            ",,",
            "1,,",
            ",1,",
            "1,2,",
            "2,1,",
            ",-2,Number of slides cannot be a negative number.",
            "2,-1,Number of slides cannot be a negative number.",
    })
    public void testUpdateNumSlides(Integer oldValue, Integer newValue, String expectedErrorMessage) {
        String workNumber = "SGP4000";
        Work work = new Work(10, workNumber, null, null, null, Status.active);
        work.setNumBlocks(oldValue);
        when(mockWorkRepo.getByWorkNumber(workNumber)).thenReturn(work);
        User user = EntityFactory.getUser();

        if (expectedErrorMessage!=null) {
            assertThat(assertThrows(IllegalArgumentException.class, () -> workService.updateWorkNumSlides(user, workNumber, newValue)))
                    .hasMessage(expectedErrorMessage);
            verify(mockWorkRepo, never()).save(any());
        } else if (Objects.equals(oldValue, newValue)) {
            assertSame(work, workService.updateWorkNumSlides(user, workNumber, newValue));
            assertEquals(work.getNumSlides(), newValue);
            verify(mockWorkRepo, never()).save(any());
        } else {
            when(mockWorkRepo.save(any())).then(Matchers.returnArgument());
            assertSame(work, workService.updateWorkNumSlides(user, workNumber, newValue));
            verify(mockWorkRepo).save(work);
            assertEquals(work.getNumSlides(), newValue);
        }
    }

    @ParameterizedTest
    @CsvSource(value={
            "sgp, ",
            "SGP, ",
            "r&d, ",
            "R&D, ",
            ", No prefix supplied for work number.",
            "Bananas, Invalid work number prefix: \"Bananas\"",
    })
    public void testCheckPrefix(String prefix, String expectedErrorMessage) {
        if (expectedErrorMessage==null) {
            workService.checkPrefix(prefix);
        } else {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> workService.checkPrefix(prefix));
            assertThat(ex).hasMessage(expectedErrorMessage);
        }
    }

    @ParameterizedTest
    @MethodSource("linkVariousArgs")
    public void testLinkVarious(Object arg, int numOps, String expectedErrorMsg) {
        String workNumber;
        Work work;
        if (arg instanceof Work) {
            work = (Work) arg;
            workNumber = work.getWorkNumber();
            when(mockWorkRepo.getByWorkNumber(workNumber)).thenReturn(work);
        } else {
            workNumber = (String) arg;
            work = null;
            when(mockWorkRepo.getByWorkNumber(workNumber)).then(invocation -> {
                throw new EntityNotFoundException("Unknown work number.");
            });
        }

        List<Operation> ops = (numOps==0 ? List.of() : List.of(new Operation()));
        if (work==null) {
            assertThat(assertThrows(EntityNotFoundException.class, () -> workService.link(workNumber, ops)))
                    .hasMessage(expectedErrorMsg);
        } else if (expectedErrorMsg==null) {
            assertSame(arg, workService.link(workNumber, ops));
        } else {
            assertThat(assertThrows(IllegalArgumentException.class, () -> workService.link(workNumber, ops)))
                    .hasMessage(expectedErrorMsg);
        }
        verify(mockWorkRepo, never()).save(any());
    }

    static Stream<Arguments> linkVariousArgs() {
        Work activeWork = new Work(1, "SGP1001", null, null, null, Status.active);
        Work pausedWork = new Work(2, "R&D1002", null, null, null, Status.paused);
        Work completedWork = new Work(3, "SGP1003", null, null, null, Status.completed);
        Work failedWork = new Work(4, "SGP1004", null, null, null, Status.failed);

        return Arrays.stream(new Object[][] {
                { activeWork, 0, null },
                { pausedWork, 1, "R&D1002 cannot be used because it is paused." },
                { completedWork, 1, "SGP1003 cannot be used because it is completed." },
                { failedWork, 1, "SGP1004 cannot be used because it is failed." },
                { "SGP404", 1, "Unknown work number." },
        }).map(Arguments::of);
    }

    @Test
    public void testLinkSuccessful() {
        Sample sam1 = EntityFactory.getSample();
        Sample sam2 = new Sample(sam1.getId()+1, sam1.getSection()+1, sam1.getTissue(), sam1.getBioState());
        LabwareType lt = EntityFactory.makeLabwareType(1,2);
        Labware lw1 = EntityFactory.makeLabware(lt, sam1, sam2);
        Labware lw2 = EntityFactory.makeEmptyLabware(lt);
        lw2.getFirstSlot().getSamples().add(sam1);
        lw2.getFirstSlot().getSamples().add(sam2);
        Labware lw0 = EntityFactory.makeLabware(EntityFactory.getTubeType(), sam1);

        OperationType opType = EntityFactory.makeOperationType("Section", null);
        Operation op1 = makeOp(opType, 10, lw0, lw1);
        Operation op2 = makeOp(opType, 11, lw0, lw2);

        Work work = new Work(50, "SGP5000", null, null, null, Status.active);

        when(mockWorkRepo.getByWorkNumber(work.getWorkNumber())).thenReturn(work);
        work.setOperationIds(List.of(1,2,3));
        work.setSampleSlotIds(List.of(new SampleSlotId(sam1.getId(), 2)));

        when(mockWorkRepo.save(any())).then(Matchers.returnArgument());

        assertEquals(work, workService.link(work.getWorkNumber(), List.of(op1, op2)));
        verify(mockWorkRepo).save(work);
        assertThat(work.getOperationIds()).containsExactlyInAnyOrder(1,2,3,10,11);
        assertThat(work.getSampleSlotIds()).containsExactlyInAnyOrder(
                new SampleSlotId(sam1.getId(), 2),
                new SampleSlotId(sam1.getId(), lw1.getFirstSlot().getId()),
                new SampleSlotId(sam2.getId(), lw1.getSlot(new Address(1,2)).getId()),
                new SampleSlotId(sam1.getId(), lw2.getFirstSlot().getId()),
                new SampleSlotId(sam2.getId(), lw2.getFirstSlot().getId())
        );
    }

    @ParameterizedTest
    @MethodSource("usableWorkArgs")
    public void testGetUsableWork(String workNumber, Status status, Class<? extends Exception> expectedExceptionType, String expectedErrorMessage) {
        if (expectedExceptionType==null) {
            Work work = new Work(14, workNumber, null, null, null, status);
            when(mockWorkRepo.getByWorkNumber(workNumber)).thenReturn(work);
            assertSame(work, workService.getUsableWork(workNumber));
            return;
        }

        if (status==null) {
            when(mockWorkRepo.getByWorkNumber(workNumber))
                    .then(invocation -> {throw new EntityNotFoundException("Work number not recognised: "+repr(workNumber)); });
        } else {
            Work work = new Work(14, workNumber, null, null, null, status);
            when(mockWorkRepo.getByWorkNumber(workNumber)).thenReturn(work);
        }
        assertThat(assertThrows(expectedExceptionType, () -> workService.getUsableWork(workNumber)))
                .hasMessage(expectedErrorMessage);
    }

    @ParameterizedTest
    @MethodSource("usableWorkArgs")
    public void testValidateUsableWork(String workNumber, Status status, Class<? extends Exception> unused, String expectedErrorMessage) {
        List<String> problems = new ArrayList<>(1);
        if (workNumber==null) {
            assertNull(workService.validateUsableWork(problems, null));
            assertThat(problems).isEmpty();
            verifyNoInteractions(mockWorkRepo);
            return;
        }

        Optional<Work> optWork = Optional.ofNullable(status).map(st -> new Work(14, workNumber, null, null, null, st));
        when(mockWorkRepo.findByWorkNumber(workNumber)).thenReturn(optWork);
        assertSame(optWork.orElse(null), workService.validateUsableWork(problems, workNumber));

        if (expectedErrorMessage==null) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactly(expectedErrorMessage);
        }
    }

    static Stream<Arguments> usableWorkArgs() {
        return Arrays.stream(new Object[][] {
                { "SGP5000", Status.active, null, null },
                { "SGP5000", Status.paused, IllegalArgumentException.class, "SGP5000 cannot be used because it is paused." },
                { "SGP5000", Status.completed, IllegalArgumentException.class, "SGP5000 cannot be used because it is completed."  },
                { "SGP5000", Status.failed, IllegalArgumentException.class, "SGP5000 cannot be used because it is failed."  },
                { "SGP404", null, EntityNotFoundException.class, "Work number not recognised: \"SGP404\"" },
                { null, null, NullPointerException.class, "Work number is null." },
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("getWorksWithCommentsArgs")
    public void testGetWorksWithComments(Collection<Status> statuses, Collection<Work> works, Collection<WorkEvent> events) {
        if (statuses==null) {
            when(mockWorkRepo.findAll()).thenReturn(works);
        } else {
            when(mockWorkRepo.findAllByStatusIn(statuses)).thenReturn(works);
        }
        Map<Integer, WorkEvent> eventMap;
        if (events==null) {
            eventMap = null;
        } else {
            eventMap = events.stream().collect(BasicUtils.toMap(e -> e.getWork().getId()));
            when(mockWorkEventService.loadLatestEvents(any())).thenReturn(eventMap);
        }
        List<WorkWithComment> wcs = workService.getWorksWithComments(statuses);
        assertThat(wcs.stream().map(WorkWithComment::getWork)).containsExactlyElementsOf(works);

        if (events!=null) {
            List<Integer> workIds = works.stream()
                    .filter(work -> work.getStatus()==Status.failed || work.getStatus()==Status.paused)
                    .map(Work::getId)
                    .collect(toList());
            verify(mockWorkEventService).loadLatestEvents(workIds);
            verify(workService).fillInComments(wcs, eventMap);
        }
    }

    static Stream<Arguments> getWorksWithCommentsArgs() {
        Work workA = new Work(1, "SGP1", null, null, null, Status.active);
        Work workC = new Work(2, "SGP2", null, null, null, Status.completed);
        Work workF = new Work(3, "SGP3", null, null, null, Status.failed);
        Work workP = new Work(4, "SGP4", null, null, null, Status.paused);

        WorkEvent eventF = new WorkEvent(workF, WorkEvent.Type.fail, null, null);
        WorkEvent eventP = new WorkEvent(workP, WorkEvent.Type.pause, null, null);

        return Arrays.stream(new Object[][] {
                {null, List.of(workA, workC, workF, workP), List.of(eventF, eventP)},
                {List.of(Status.active, Status.completed), List.of(workA, workC), null},
                {List.of(Status.failed, Status.paused), List.of(workF, workP), List.of(eventF, eventP)},
        }).map(Arguments::of);
    }

    @Test
    public void testFillInComments() {
        Work workF1 = new Work(1, "SGP1", null, null, null, Status.failed);
        Work workF2 = new Work(2, "SGP2", null, null, null, Status.failed);
        Work workP1 = new Work(3, "SGP3", null, null, null, Status.paused);
        Work workP2 = new Work(4, "SGP4", null, null, null, Status.paused);
        Map<Integer, WorkEvent> events = Stream.of(
                new WorkEvent(workF1, WorkEvent.Type.fail, null, new Comment(1, "Ohio", "")),
                new WorkEvent(workF2, WorkEvent.Type.create, null, new Comment(2, "Oklahoma", "")),
                new WorkEvent(workP1, WorkEvent.Type.pause, null, new Comment(3, "Oregon", ""))
        ).collect(BasicUtils.toMap(e -> e.getWork().getId()));

        List<WorkWithComment> wcs = Stream.of(workF1, workF2, workP1, workP2)
                .map(WorkWithComment::new)
                .collect(toList());

        workService.fillInComments(wcs, events);

        assertEquals(List.of(new WorkWithComment(workF1, "Ohio"), new WorkWithComment(workF2),
                        new WorkWithComment(workP1, "Oregon"), new WorkWithComment(workP2)),
                wcs);
    }

    private Operation makeOp(OperationType opType, int opId, Labware srcLw, Labware dstLw) {
        List<Action> actions = new ArrayList<>();
        int acId = 100*opId;
        for (Slot src : srcLw.getSlots()) {
            for (Sample sam0 : src.getSamples()) {
                for (Slot dst : dstLw.getSlots()) {
                    for (Sample sam1 : dst.getSamples()) {
                        Action action = new Action(acId, opId, src, dst, sam1, sam0);
                        actions.add(action);
                        ++acId;
                    }
                }
            }
        }
        return new Operation(opId, opType, null, actions, EntityFactory.getUser());
    }
}
