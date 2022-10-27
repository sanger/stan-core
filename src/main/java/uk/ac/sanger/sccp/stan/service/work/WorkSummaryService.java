package uk.ac.sanger.sccp.stan.service.work;

import uk.ac.sanger.sccp.stan.request.WorkSummaryData;

public interface WorkSummaryService {
    /**
     * Gets the summary of all works.
     * @return summary of all works
     */
    WorkSummaryData loadWorkSummary();
}
