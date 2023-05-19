package uk.ac.sanger.sccp.stan.service.work;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.Work.Status;
import uk.ac.sanger.sccp.stan.repo.WorkRepo;
import uk.ac.sanger.sccp.stan.repo.WorkTypeRepo;
import uk.ac.sanger.sccp.stan.request.WorkSummaryData;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.*;

/**
 * Tests {@link WorkSummaryServiceImp}
 */
public class TestWorkSummaryService {
    private WorkRepo mockWorkRepo;
    private WorkTypeRepo mockWorkTypeRepo;
    private WorkSummaryServiceImp service;
    private int idCounter;

    @BeforeEach
    void setup() {
        mockWorkRepo = mock(WorkRepo.class);
        mockWorkTypeRepo = mock(WorkTypeRepo.class);
        service = spy(new WorkSummaryServiceImp(mockWorkRepo, mockWorkTypeRepo));
        idCounter = 10;
    }

    @Test
    public void testLoadWorkSummary() {
        WorkType workType = new WorkType(1, "Frying");

        List<Work> works = List.of(work(workType, Status.active, 1, null, 3));

        when(mockWorkRepo.findAll()).thenReturn(works);
        when(mockWorkTypeRepo.findAll()).thenReturn(List.of(workType));
        WorkSummaryGroup wsg = new WorkSummaryGroup(workType, Status.active, 1, 1, 0, 3);
        assertEquals(service.loadWorkSummary(), new WorkSummaryData(List.of(workType), List.of(wsg)));

        verify(service).createGroups(works);
    }

    @Test
    public void testCreateGroups() {
        WorkType wt1 = new WorkType(1, "Frying");
        WorkType wt2 = new WorkType(2, "Baking");
        List<Work> works = List.of(
                work(wt1, Status.active, 2, null, 0),
                work(wt1, Status.completed, 0, 3, null),
                work(wt2, Status.active, null, 0, 4),
                work(wt1, Status.active, 0, 5, 0),
                work(wt1, Status.active, 5, 6, 7),
                work(wt2, Status.failed, 0, 0, 6)
        );
        assertThat(service.createGroups(works)).containsExactlyInAnyOrder(
                new WorkSummaryGroup(wt1, Status.active, 3, 7, 11, 7),
                new WorkSummaryGroup(wt1, Status.completed, 1, 0, 3, 0),
                new WorkSummaryGroup(wt2, Status.active, 1, 0, 0, 4),
                new WorkSummaryGroup(wt2, Status.failed, 1, 0, 0, 6)
        );
    }

    @ParameterizedTest
    @CsvSource({",0", "1,1", "2,2", "-3,-3"})
    public void testIntOrZero(Integer arg, int expected) {
        assertEquals(expected, WorkSummaryServiceImp.intOrZero(arg));
    }

    @ParameterizedTest
    @CsvSource({
            "1, 1, active, active, true",
            "1, 2, active, active, false",
            "2, 2, failed, failed, true",
            "3, 3, active, failed, false",
    })
    public void testGroupKeys(int type1, int type2, Status st1, Status st2, boolean equal) {
        WorkType wt1 = new WorkType(type1, "wt"+type1);
        WorkType wt2 = new WorkType(type2, "wt"+type2);
        Work work1 = work(wt1, st1, 1, 0, 0);
        Work work2 = work(wt2, st2, 0, 2, 0);
        var key1 = service.groupKey(work1);
        var key2 = service.groupKey(work2);
        if (equal) {
            assertEquals(key1, key2);
            assertEquals(key1.hashCode(), key2.hashCode());
        } else {
            assertNotEquals(key1, key2);
        }
    }

    private Work work(WorkType wt, Status status, Integer numBlocks, Integer numSlides, Integer numOriginal) {
        int id = ++idCounter;
        return new Work(id, "SGP"+id, wt, null, null, null, null, status,
                numBlocks, numSlides, numOriginal, null, null, null);
    }

}