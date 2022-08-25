package uk.ac.sanger.sccp.stan.service.work;

import uk.ac.sanger.sccp.stan.model.WorkSummaryGroup;

import java.util.Collection;

public interface WorkSummaryService {
    /**
     * Gets the summary of all works.
     * @return summary of all works
     */
    Collection<WorkSummaryGroup> loadWorkSummary();
}
