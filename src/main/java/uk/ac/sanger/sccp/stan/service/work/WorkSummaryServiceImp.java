package uk.ac.sanger.sccp.stan.service.work;

import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.Work;
import uk.ac.sanger.sccp.stan.model.WorkSummaryGroup;
import uk.ac.sanger.sccp.stan.model.WorkType;
import uk.ac.sanger.sccp.stan.repo.WorkRepo;
import uk.ac.sanger.sccp.stan.repo.WorkTypeRepo;
import uk.ac.sanger.sccp.stan.request.WorkSummaryData;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.*;

/**
 * @author dr6
 */
@Service
public class WorkSummaryServiceImp implements WorkSummaryService {
    private final WorkRepo workRepo;
    private final WorkTypeRepo workTypeRepo;

    public WorkSummaryServiceImp(WorkRepo workRepo, WorkTypeRepo workTypeRepo) {
        this.workRepo = workRepo;
        this.workTypeRepo = workTypeRepo;
    }

    @Override
    public WorkSummaryData loadWorkSummary() {
        List<WorkType> workTypes = BasicUtils.asList(workTypeRepo.findAll());
        Iterable<Work> works = workRepo.findAll();
        List<WorkSummaryGroup> workSummaryGroups = new ArrayList<>(createGroups(works));

        return new WorkSummaryData(workTypes, workSummaryGroups);
    }

    /**
     * Summarises the works into summary groups.
     * @param works the works to summaries
     * @return the summary groups for the given works
     */
    public Collection<WorkSummaryGroup> createGroups(Iterable<Work> works) {
        Map<GroupKey, WorkSummaryGroup> groupMap = new HashMap<>();
        for (Work work : works) {
            GroupKey key = groupKey(work);
            WorkSummaryGroup group = groupMap.get(key);
            int numSlides = intOrZero(work.getNumSlides());
            int numBlocks = intOrZero(work.getNumBlocks());
            int numOriginalSamples = intOrZero(work.getNumOriginalSamples());
            if (group==null) {
                group = new WorkSummaryGroup(work.getWorkType(), work.getStatus(), 1,
                        numBlocks, numSlides, numOriginalSamples);
                groupMap.put(key, group);
            } else {
                group.setNumWorks(group.getNumWorks()+1);
                group.setTotalNumSlides(group.getTotalNumSlides() + numSlides);
                group.setTotalNumBlocks(group.getTotalNumBlocks() + numBlocks);
                group.setTotalNumOriginalSamples(group.getTotalNumOriginalSamples() + numOriginalSamples);
            }
        }
        return groupMap.values();
    }

    /**
     * Gets the int value of a number, or zero if the number is null
     * @param number a number object that may be null
     * @return the int value of the number, or zero
     */
    public static int intOrZero(Number number) {
        return (number==null ? 0 : number.intValue());
    }

    /**
     * Gets the correct group key for the given work.
     * @param work the work to find the group key for
     * @return the correct group key for the work
     */
    public GroupKey groupKey(Work work) {
        return new GroupKey(work.getWorkType().getId(), work.getStatus());
    }

    /** The key by which summary groups are distinguished. */
    public record GroupKey(int workTypeId, Work.Status status) {}
}
