package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.Measurement;

import java.util.Optional;

/**
 * Service to help with {@link Measurement measurements}
 */
public interface MeasurementService {
    /**
     * Gets the named measurement from the indicated labware or its parent labware.
     * @param barcode the labware barcode
     * @param name the name of the measurement
     * @return the measurement, if one was found
     * @exception javax.persistence.EntityNotFoundException if the indicated labware does not exist
     */
    Optional<Measurement> getMeasurementFromLabwareOrParent(String barcode, String name);
}
