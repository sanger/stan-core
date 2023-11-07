package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.DnapStudy;

import java.util.List;

public interface SSStudyService {

    /**
     * Updates the internal table of studies from the multi-lims warehouse.
     * Transacts internally.
     */
    void updateStudies();

    /**
     * Gets the list of enabled studies in Stan's database. Provided here for convenience
     * for callers of {@link #updateStudies}.
     * @return the list of enabled studies in Stan's database
     */
    List<DnapStudy> loadEnabledStudies();
}
