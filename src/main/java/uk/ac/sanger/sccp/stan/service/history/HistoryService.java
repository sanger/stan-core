package uk.ac.sanger.sccp.stan.service.history;

import uk.ac.sanger.sccp.stan.request.History;

/**
 * Service for getting the history from some kind of identifier.
 * The history typically includes all related samples.
 * Methods may throw {@link javax.persistence.EntityNotFoundException}.
 */
public interface HistoryService {
    /**
     * Gets the history for a particular sample (and related samples)
     * @param sampleId the id of the sample
     * @return the history for related samples
     */
    History getHistoryForSampleId(int sampleId);

    /**
     * Gets the history for samples derived from tissue identified by its external name
     * @param externalName the name of tissue
     * @return the history for samples from that tissue
     */
    History getHistoryForExternalName(String externalName);

    /**
     * Gets the history for samples derived from a specified donor
     * @param donorName the name of a donor
     * @return the history for samples from that donor
     */
    History getHistoryForDonorName(String donorName);

    /**
     * Gets history for samples related to samples in a specified piece of labware
     * @param barcode the barcode of a piece of labware
     * @return the history for samples in the labware (and related samples)
     */
    History getHistoryForLabwareBarcode(String barcode);

    /**
     * Gets the history for a work number
     * @param workNumber a valid work number
     * @return the history of operations done for that work number
     */
    History getHistoryForWorkNumber(String workNumber);

}
