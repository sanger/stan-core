package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.Work.Status;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.WorkProgress;
import uk.ac.sanger.sccp.stan.request.WorkProgress.WorkProgressTimestamp;

import javax.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * Tests {@link WorkProgressServiceImp}
 * @author dr6
 */
public class TestWorkProgressService {
    private WorkRepo mockWorkRepo;
    private WorkTypeRepo mockWorkTypeRepo;
    private OperationRepo mockOpRepo;

    private WorkProgressServiceImp service;

    @BeforeEach
    void setup() {
        mockWorkRepo = mock(WorkRepo.class);
        mockWorkTypeRepo = mock(WorkTypeRepo.class);
        mockOpRepo = mock(OperationRepo.class);

        service = spy(new WorkProgressServiceImp(mockWorkRepo, mockWorkTypeRepo, mockOpRepo));
    }

    @ParameterizedTest
    @MethodSource("getProgressWithWorkNumberArgs")
    public void testGetProgressWithWorkNumber(String workNumber, Work work, String workTypeName, WorkType workType,
                                             Status status, List<Work> works, String expectedError) {
        if (work==null) {
            when(mockWorkRepo.getByWorkNumber(workNumber)).thenThrow(new EntityNotFoundException("Unknown work number: "+workNumber));
        } else {
            when(mockWorkRepo.getByWorkNumber(workNumber)).thenReturn(work);
        }
        List<String> workTypeNames = (workTypeName==null ? null : workTypeName.isEmpty() ? List.of() : List.of(workTypeName));
        List<Status> statuses = (status==null ? null : List.of(status));
        mockWorkType(workTypeName, workType);
        if (expectedError!=null) {
            assertThat(assertThrows(EntityNotFoundException.class, () -> service.getProgress(workNumber, workTypeNames, statuses)))
                    .hasMessage(expectedError);
            verify(service, never()).getProgressForWork(any());
        } else {
            List<WorkProgress> wps = service.getProgress(workNumber, workTypeNames, statuses);
            verifyProgress(wps, works);
        }
    }

    static Stream<Arguments> getProgressWithWorkNumberArgs() {
        Work work = workWithId(6);
        WorkType wt1 = new WorkType(2, "California");
        String wtn = wt1.getName();
        WorkType wt2 = new WorkType(3, "Colorado");
        work.setWorkType(wt1);
        work.setStatus(Status.active);
        String wn = "SGP1";
        work.setWorkNumber(wn);
        List<Work> works = List.of(work);

        return Arrays.stream(new Object[][] {
                {wn, work, null, null, null, works, null},
                {wn, work, null, null, Status.active, works, null},
                {wn, work, "", null, Status.active, List.of(), null},
                {wn, work, null, null, Status.paused, List.of(), null},
                {wn, work, wtn, wt1, null, works, null},
                {wn, work, wtn, wt1, Status.active, works, null},
                {wn, work, wtn, wt1, Status.paused, List.of(), null},
                {wn, work, "Colorado", wt2, null, List.of(), null},
                {wn, work, "Colorado", wt2, Status.active, List.of(), null},
                {wn, work, "Colorado", wt2, Status.paused, List.of(), null},

                {"SGP404", null, null, null, null,  null, "Unknown work number: SGP404"},
                {wn, work, "France", null, null, null, "Unknown work types: [France]"},
                {wn, work, "France", null, Status.paused, null, "Unknown work types: [France]"},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("getProgressWithWorkTypeNamesArgs")
    public void testGetProgressWithWorkTypeNames(String workTypeName, WorkType workType, Status status,
                                                List<Work> works, List<Work> expectedWorks, String expectedError) {
        mockWorkType(workTypeName, workType);
        List<String> workTypeNames = workTypeName==null ? null : workTypeName.isEmpty() ? List.of() : List.of(workTypeName);
        List<Status> statuses = status==null ? null : List.of(status);
        if (expectedError!=null) {
            assertThat(assertThrows(EntityNotFoundException.class,
                    () -> service.getProgress(null, workTypeNames, statuses)))
                    .hasMessage(expectedError);
        } else if (workType!=null) {
            when(mockWorkRepo.findAllByWorkTypeIn(List.of(workType))).thenReturn(works);
            List<WorkProgress> wps = service.getProgress(null, workTypeNames, statuses);
            verifyProgress(wps, expectedWorks);
        } else {
            List<WorkProgress> wps = service.getProgress(null, workTypeNames, statuses);
            assertThat(wps).isEmpty();
        }
    }

    static Stream<Arguments> getProgressWithWorkTypeNamesArgs() {
        WorkType wt = new WorkType(2, "California");
        List<Work> works = List.of(workWithId(1), workWithId(2));
        works.get(0).setStatus(Status.active);
        works.get(1).setStatus(Status.paused);

        return Arrays.stream(new Object[][] {
                {"California", wt, null, works, works, null},
                {"", null, null, works, null, null},
                {"California", wt, Status.active, works, List.of(works.get(0)), null},
                {"France", null, null, null, null, "Unknown work types: [France]"},
        }).map(Arguments::of);
    }

    @Test
    public void testGetProgressWithStatuses() {
        List<Work> works = List.of(workWithId(1), workWithId(2));
        when(mockWorkRepo.findAllByStatusIn(List.of(Status.active, Status.unstarted))).thenReturn(works);
        List<WorkProgress> wps = service.getProgress(null, null, List.of(Status.active, Status.unstarted));
        verifyProgress(wps, works);
    }

    @Test
    public void testGetProgressWithEmptyListOfStatuses() {
        List<WorkProgress> wps = service.getProgress(null, null, List.of());
        assertThat(wps).isEmpty();
    }

    @Test
    public void testGetProgressWithNoFilter() {
        List<Work> works = List.of(workWithId(1), workWithId(2));
        when(mockWorkRepo.findAll()).thenReturn(works);
        List<WorkProgress> wps = service.getProgress(null, null, null);
        verifyProgress(wps, works);
    }

    private void mockWorkType(String workTypeName, WorkType workType) {
        if (workType != null) {
            when(mockWorkTypeRepo.getByName(workTypeName)).thenReturn(workType);
            when(mockWorkTypeRepo.getAllByNameIn(List.of(workTypeName))).thenReturn(List.of(workType));
        } else if (workTypeName!=null) {
            when(mockWorkTypeRepo.getByName(workTypeName)).thenThrow(new EntityNotFoundException("Unknown work type: "+workTypeName));
            final List<String> workTypeNames = List.of(workTypeName);
            when(mockWorkTypeRepo.getAllByNameIn(workTypeNames)).thenThrow(new EntityNotFoundException("Unknown work types: "+workTypeNames));
        }
    }

    private void verifyProgress(List<WorkProgress> wps, List<Work> works) {
        assertThat(wps.stream().map(WorkProgress::getWork)).containsExactlyElementsOf(works);
        verify(service, times(works.size())).getProgressForWork(any());
    }

    @Test
    public void testGetProgressForWork() {
        Work work = workWithId(17);
        final LocalDateTime sectionTime = LocalDateTime.of(2021, 9, 23, 11, 0);
        final LocalDateTime stainTime = LocalDateTime.of(2021, 9, 22, 12, 0);
        Map<String, LocalDateTime> times = Map.of("Section", sectionTime,"Stain", stainTime);
        doReturn(times).when(service).loadOpTimes(work);
        WorkProgress wp = service.getProgressForWork(work);
        assertSame(work, wp.getWork());
        assertThat(wp.getTimestamps()).containsExactlyInAnyOrder(
                new WorkProgressTimestamp("Section", sectionTime),
                new WorkProgressTimestamp("Stain", stainTime)
        );
    }

    @Test
    public void testLoadOpTimes() {
        Work work = workWithId(17);
        List<Integer> opIds = List.of(5,6,7,8,9);
        work.setOperationIds(opIds);
        OperationType sectionType = new OperationType(1, "Section");
        OperationType stainType = new OperationType(2, "Stain");
        OperationType otherType = new OperationType(3, "Bananas");

        OperationType[] opTypes = { sectionType, stainType, otherType, sectionType, sectionType };
        LocalDateTime[] times = IntStream.of(10,11,12,13,9)
                .mapToObj(d -> LocalDateTime.of(2021,9, d, 12,0))
                .toArray(LocalDateTime[]::new);
        List<Operation> ops = IntStream.range(0, opTypes.length)
                .mapToObj(i -> {
                    Operation op = new Operation();
                    op.setId(opIds.get(i));
                    op.setOperationType(opTypes[i]);
                    op.setPerformed(times[i]);
                    return op;
                }).collect(toList());
        when(mockOpRepo.findAllById(opIds)).thenReturn(ops);

        var result = service.loadOpTimes(work);
        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of("Section", times[3], "Stain", times[1]));
    }

    private static Work workWithId(int id) {
        Work work = new Work();
        work.setId(id);
        return work;
    }

}
