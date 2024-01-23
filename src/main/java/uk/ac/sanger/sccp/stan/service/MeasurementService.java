package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.model.Measurement;
import uk.ac.sanger.sccp.stan.request.AddressString;

import java.util.List;
import java.util.Map;

/**
 * Service to help with {@link Measurement measurements}
 */
public interface MeasurementService {
    /**
     * Gets the named measurements from the each slot of the indicated labware or its parent labware.
     * @param barcode the labware barcode
     * @param name the name of the measurement
     * @return map from slot address to measurements
     * @exception javax.persistence.EntityNotFoundException if the indicated labware does not exist
     */
    Map<Address, List<Measurement>> getMeasurementsFromLabwareOrParent(String barcode, String name);

    /**
     * Converts a map of address to measurements to a list with one value for each address
     * @param map a map from slot address to measurements
     * @return a list of addresses and strings
     */
    List<AddressString> toAddressStrings(Map<Address, List<Measurement>> map);
}
