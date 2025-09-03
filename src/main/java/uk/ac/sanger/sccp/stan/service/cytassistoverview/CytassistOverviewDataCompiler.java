package uk.ac.sanger.sccp.stan.service.cytassistoverview;

import uk.ac.sanger.sccp.stan.model.CytassistOverview;

import java.util.List;

/**
 * Service that loads the data for the cytassist overview table
 * @author dr6
 */
public interface CytassistOverviewDataCompiler {
    /**
     * Compiles the cytassist overview data
     */
    List<CytassistOverview> execute();
}
