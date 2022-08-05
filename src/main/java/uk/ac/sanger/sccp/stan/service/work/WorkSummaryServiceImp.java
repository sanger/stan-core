package uk.ac.sanger.sccp.stan.service.work;

import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.Work;
import uk.ac.sanger.sccp.stan.model.WorkSummaryGroup;
import uk.ac.sanger.sccp.stan.repo.WorkRepo;

import java.util.*;
import java.util.stream.Stream;

/**
 * @author dr6
 */
@Service
public class WorkSummaryServiceImp implements WorkSummaryService {
    private final WorkRepo workRepo;

    public WorkSummaryServiceImp(WorkRepo workRepo) {
        this.workRepo = workRepo;
    }

    @Override
    public Collection<WorkSummaryGroup> loadWorkSummary() {
        Iterable<Work> works = workRepo.findAll();
        return createGroups(works);
    }

    /**
     * Summarises the works into summary groups.
     * @param works the works to summaries
     * @return the summary groups for the given works
     */
    public Collection<WorkSummaryGroup> createGroups(Iterable<Work> works) {
        Map<GroupKey, WorkSummaryGroup> groupMap = new HashMap<>();
        for (Work work : works) {
            int lwNum = totalLabwareRequired(work);
            GroupKey key = groupKey(work);
            WorkSummaryGroup group = groupMap.get(key);
            if (group==null) {
                group = new WorkSummaryGroup(work.getWorkType(), work.getStatus(), 1, lwNum);
                groupMap.put(key, group);
            } else {
                group.setNumWorks(group.getNumWorks()+1);
                group.setTotalLabwareRequired(group.getTotalLabwareRequired() + lwNum);
            }
        }
        return groupMap.values();
    }

    /**
     * Finds the total labware required (numBlocks plus numSlides plus numOriginalSamples)
     * for the work.
     * @param work the work
     * @return the total number of labware required, specified in the work
     */
    public int totalLabwareRequired(Work work) {
        return Stream.of(work.getNumBlocks(), work.getNumSlides(), work.getNumOriginalSamples())
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    /**
     * Gets the correct group key for the given work.
     * @param work the work to find the group key for
     * @return the correct group key for the work
     */
    public GroupKey groupKey(Work work) {
        return new GroupKey(work.getWorkType().getId(), work.getStatus());
    }

    /**
     * The key by which summary groups are distinguished.
     */
    public static class GroupKey {
        int workTypeId;
        Work.Status status;

        GroupKey(int workTypeId, Work.Status status) {
            this.workTypeId = workTypeId;
            this.status = status;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GroupKey that = (GroupKey) o;
            return (this.workTypeId == that.workTypeId
                    && this.status == that.status);
        }

        @Override
        public int hashCode() {
            return 31*workTypeId + status.hashCode();
        }
    }
}
