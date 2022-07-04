package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.request.NextReplicateData;

import java.util.Collection;
import java.util.List;

/**
 * Service for calculating next replicate data.
 */
public interface NextReplicateService {
    /**
     * Gets the next replicate numbers for the tissue groups for the given
     * source barcodes.
     * @param barcodes labware barcodes
     * @return next replicate data for the given barcodes
     */
    List<NextReplicateData> getNextReplicateData(Collection<String> barcodes);
}
