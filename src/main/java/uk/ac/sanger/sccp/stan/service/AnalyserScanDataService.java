package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.request.AnalyserScanData;

import javax.persistence.EntityNotFoundException;

/**
 * Service dealing with AnalyserScanData
 * @author dr6
 */
public interface AnalyserScanDataService {
    /**
     * Loads data for the labware with the given barcode
     * @param barcode labware barcode
     * @return the data for the specified labware
     * @exception EntityNotFoundException the labware barcode is not recognised
     */
    AnalyserScanData load(String barcode) throws EntityNotFoundException;
}
