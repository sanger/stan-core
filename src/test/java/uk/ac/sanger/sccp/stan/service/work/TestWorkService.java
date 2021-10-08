package uk.ac.sanger.sccp.stan.service.work;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentMatcher;
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.Matchers;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.Work.SampleSlotId;
import uk.ac.sanger.sccp.stan.model.Work.Status;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.utils.UCMap;

import javax.persistence.EntityNotFoundException;
import java.util.*;
import java.util.stream.IntStream;
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

    @Test
    public void testCreateWork() {
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

        Work result = workService.createWork(user, prefix, workTypeName, projectName, code);
        verify(workService).checkPrefix(prefix);
        verify(mockWorkRepo).createNumber(prefix);
        verify(mockWorkRepo).save(result);
        verify(mockWorkEventService).recordEvent(user, result, WorkEvent.Type.create, null);
        assertEquals(new Work(null, workNumber, workType, project, cc, Status.active), result);
    }

    @ParameterizedTest
    @ValueSource(booleans={true, false})
    public void testUpdateStatus(boolean success) {
        User user = new User(1, "user1", User.Role.admin);
        String workNumber = "SGP4000";
        final Integer commentId = 99;
        Status newStatus = Status.paused;
        Work work = new Work(10, workNumber, null, null, null, Status.active);
        when(mockWorkRepo.getByWorkNumber(workNumber)).thenReturn(work);
        if (!success) {
            doThrow(IllegalArgumentException.class).when(mockWorkEventService).recordStatusChange(any(), any(), any(), any());
            assertThrows(IllegalArgumentException.class, () -> workService.updateStatus(user, workNumber, newStatus, commentId));
            verify(mockWorkRepo, never()).save(any());
        } else {
            when(mockWorkRepo.save(any())).then(Matchers.returnArgument());
            assertSame(work, workService.updateStatus(user, workNumber, newStatus, commentId));
            verify(mockWorkRepo).save(work);
            assertEquals(work.getStatus(), newStatus);
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
            assertNull(workService.validateUsableWork(problems, workNumber));
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
    @MethodSource("usableWorksArgs")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testValidateUsableWorks(Object[] workNumbers, Object[] workData, Object[] expectedErrors) {
        assert workData.length%2==0;
        List<Work> works = IntStream.range(0, workData.length/2)
                .mapToObj(i -> new Work(i, (String) workData[2*i], null, null, null, (Status) workData[2*i+1]))
                .collect(toList());
        List<String> workNumbersList = (List) Arrays.asList(workNumbers);
        when(mockWorkRepo.findAllByWorkNumberIn(workNumbersList)).thenReturn(works);

        final List<String> problems = new ArrayList<>(expectedErrors.length);
        UCMap<Work> workMap = workService.validateUsableWorks(problems, workNumbersList);

        if (!workNumbersList.isEmpty()) {
            verify(mockWorkRepo).findAllByWorkNumberIn(workNumbersList);
        }
        assertThat(workMap.values()).containsExactlyInAnyOrderElementsOf(works);
        if (expectedErrors.length==1 && expectedErrors[0] instanceof ArgumentMatcher) {
            ArgumentMatcher<String> matcher = (ArgumentMatcher<String>) expectedErrors[0];
            assertThat(problems).isNotEmpty().allMatch(matcher::matches);
        } else {
            assertThat(problems).containsExactlyInAnyOrderElementsOf((List) Arrays.asList(expectedErrors));
        }
    }

    static Stream<Arguments> usableWorksArgs() {
        return Arrays.stream(new Object[][][] {
                {{},{},{}},
                {{"SGP1", "sgp2", "SGP2"}, {"SGP1", Status.active, "SGP2", Status.active}, {}},
                {{"SGP1", "SGP2", "SGP404", "SGP405"}, {"SGP1", Status.active, "SGP2", Status.active},
                        {"Work numbers not recognised: [\"SGP404\", \"SGP405\"]"}},
                {{"SGP1", "SGP10", "SGP11"}, {"SGP1", Status.active, "SGP10", Status.paused, "SGP11", Status.paused},
                        {"Work numbers cannot be used because they are paused: [SGP10, SGP11]"}},
                {{"SGP10", "sgp10"}, {"SGP10", Status.failed},
                        {"Work number cannot be used because it is failed: [SGP10]"}},
                {{"SGP1", "sgp1", "SGP404", "sgp404", "SGP10"}, {"SGP1", Status.active, "SGP10", Status.failed},
                        {"Work number not recognised: [\"SGP404\"]",
                                "Work number cannot be used because it is failed: [SGP10]"}},
                {{"SGP1", "SGP10", "SGP11", "SGP12"},
                        {"SGP1", Status.active, "SGP10", Status.failed, "SGP11", Status.completed, "SGP12", Status.paused},
                {new Matchers.DisorderedStringMatcher("Work numbers cannot be used because they are %, % or %: \\[%, %, %]".replace("%", "(\\S+)"),
                        List.of("failed", "completed", "paused", "SGP10", "SGP11", "SGP12"))}},
        }).map(Arguments::of);
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
