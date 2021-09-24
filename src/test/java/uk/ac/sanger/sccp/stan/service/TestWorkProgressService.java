package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.model.*;
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
                                             List<Work> works, String expectedError) {
        if (work==null) {
            when(mockWorkRepo.getByWorkNumber(workNumber)).thenThrow(new EntityNotFoundException("Unknown work number: "+workNumber));
        } else {
            when(mockWorkRepo.getByWorkNumber(workNumber)).thenReturn(work);
        }
        mockWorkType(workTypeName, workType);
        if (expectedError!=null) {
            assertThat(assertThrows(EntityNotFoundException.class, () -> service.getProgress(workNumber, workTypeName)))
                    .hasMessage(expectedError);
            verify(service, never()).getProgressForWork(any());
        } else {
            List<WorkProgress> wps = service.getProgress(workNumber, workTypeName);
            verifyProgress(wps, works);
        }
    }

    static Stream<Arguments> getProgressWithWorkNumberArgs() {
        Work work = workWithId(6);
        WorkType wt1 = new WorkType(2, "California");
        String wtn = wt1.getName();
        WorkType wt2 = new WorkType(3, "Colorado");
        work.setWorkType(wt1);
        String wn = "SGP1";
        work.setWorkNumber(wn);
        List<Work> works = List.of(work);

        return Arrays.stream(new Object[][] {
                {wn, work, null, null, works, null},
                {wn, work, wtn, wt1, works, null},
                {wn, work, "Colorado", wt2, List.of(), null},

                {"SGP404", null, null, null, null, "Unknown work number: SGP404"},
                {wn, work, "France", null, null, "Unknown work type: France"},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("getProgressWithWorkTypeNameArgs")
    public void testGetProgressWithWorkTypeName(String workTypeName, WorkType workType,
                                                List<Work> works, String expectedError) {
        mockWorkType(workTypeName, workType);
        if (expectedError!=null) {
            assertThat(assertThrows(EntityNotFoundException.class, () -> service.getProgress(null, workTypeName)))
                    .hasMessage(expectedError);
        } else {
            when(mockWorkRepo.findAllByWorkTypeIn(List.of(workType))).thenReturn(works);
            List<WorkProgress> wps = service.getProgress(null, workTypeName);
            verifyProgress(wps, works);
        }
    }

    static Stream<Arguments> getProgressWithWorkTypeNameArgs() {
        WorkType wt = new WorkType(2, "California");
        List<Work> works = List.of(workWithId(1), workWithId(2));

        return Arrays.stream(new Object[][] {
                {"California", wt, works, null},
                {"France", null, null, "Unknown work type: France"},
        }).map(Arguments::of);
    }

    @Test
    public void testGetProgressWithNoFilter() {
        List<Work> works = List.of(workWithId(1), workWithId(2));
        when(mockWorkRepo.findAll()).thenReturn(works);
        List<WorkProgress> wps = service.getProgress(null, null);
        verifyProgress(wps, works);
    }

    private void mockWorkType(String workTypeName, WorkType workType) {
        if (workType != null) {
            when(mockWorkTypeRepo.getByName(workTypeName)).thenReturn(workType);
        } else if (workTypeName!=null) {
            when(mockWorkTypeRepo.getByName(workTypeName)).thenThrow(new EntityNotFoundException("Unknown work type: "+workTypeName));
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
