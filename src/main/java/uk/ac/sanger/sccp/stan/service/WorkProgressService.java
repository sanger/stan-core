package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.Work;
import uk.ac.sanger.sccp.stan.request.WorkProgress;

import java.util.List;

/**
 * Service to answer queries about the progress of {@link Work}.
 * @author dr6
 */
public interface WorkProgressService {
    /**
     * Gets the applicable work progresses.
     * @param workNumber the specific work number (if any) to look up, or null
     * @param workTypeNames the work type (if any) to look up, or null
     * @param programNames the names of the programs to look up, or null
     * @param statuses the statuses of works to look up, or null
     * @param requesterNames the requesters of works to look up, or null
     * @return a list of work progresses for matching works.
     */
    List<WorkProgress> getProgress(String workNumber, List<String> workTypeNames, List<String> programNames,
                                   List<Work.Status> statuses, List<String> requesterNames);
}
