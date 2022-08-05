package uk.ac.sanger.sccp.stan.service.work;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.Work.Status;
import uk.ac.sanger.sccp.stan.repo.WorkRepo;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.*;

public class TestWorkSummaryService {
    private WorkRepo mockWorkRepo;
    private WorkSummaryServiceImp service;
    private int idCounter;

    @BeforeEach
    void setup() {
        mockWorkRepo = mock(WorkRepo.class);
        service = spy(new WorkSummaryServiceImp(mockWorkRepo));
        idCounter = 10;
    }

    @Test
    public void testLoadWorkSummary() {
        WorkType workType = new WorkType(1, "Frying");

        List<Work> works = List.of(work(workType, Status.active, 1, 2, 3));

        when(mockWorkRepo.findAll()).thenReturn(works);
        assertThat(service.loadWorkSummary()).containsExactly(new WorkSummaryGroup(workType, Status.active, 1, 6));

        verify(service).createGroups(works);
    }

    @Test
    public void testCreateGroups() {
        WorkType wt1 = new WorkType(1, "Frying");
        WorkType wt2 = new WorkType(2, "Baking");
        List<Work> works = List.of(
                work(wt1, Status.active, 2, 0, 0),
                work(wt1, Status.completed, 0, 3, 0),
                work(wt2, Status.active, 0, 0, 4),
                work(wt1, Status.active, 0, 5, 0),
                work(wt2, Status.failed, 0, 0, 6)
        );
        assertThat(service.createGroups(works)).containsExactlyInAnyOrder(
                new WorkSummaryGroup(wt1, Status.active, 2, 7),
                new WorkSummaryGroup(wt1, Status.completed, 1, 3),
                new WorkSummaryGroup(wt2, Status.active, 1, 4),
                new WorkSummaryGroup(wt2, Status.failed, 1, 6)
        );
    }

    @ParameterizedTest
    @CsvSource({"0,0,0,0", "3,0,0,3", "0,4,0,4", "0,0,7,7", "1,2,3,6", ",,,0", ",2,,2"})
    public void testTotalLabwareRequired(Integer numBlocks, Integer numSlides, Integer numOriginal, int expected) {
        Work work = work(null, null, numBlocks, numSlides, numOriginal);
        assertEquals(expected, service.totalLabwareRequired(work));
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
        return new Work(id, "SGP"+id, wt, null, null, null, status,
                numBlocks, numSlides, numOriginal, null);
    }

}