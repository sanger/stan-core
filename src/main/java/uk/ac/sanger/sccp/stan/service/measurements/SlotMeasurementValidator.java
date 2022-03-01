package uk.ac.sanger.sccp.stan.service.measurements;

import uk.ac.sanger.sccp.stan.model.Labware;
import uk.ac.sanger.sccp.stan.request.SlotMeasurementRequest;
import uk.ac.sanger.sccp.stan.service.sanitiser.Sanitiser;

import java.util.*;

public interface SlotMeasurementValidator {
    /**
     * Specifies the sanitiser to use for values of the given measurement.
     *
     * @param measurementName the name of a measurement
     * @param sanitiser       a sanitiser for values of that measurement.
     */
    void setValueSanitiser(String measurementName, Sanitiser<String> sanitiser);

    /**
     * Checks and sanitises the given measurements.
     * Problems can be subsequently retrieved from {@link #compileProblems}.
     * <p>Problems include:<ul>
     * <li>Missing names, values and addresses</li>
     * <li>Invalid names, values and addresses</li>
     * <li>Dupes (same measurement name in the same slot of the same labware)</li>
     * </ul>
     * The list returned will be empty if none of the measurements requested could be sanitised
     * @param lw  the labware for the measurements
     * @param sms the slot measurements requested for this labware
     * @return the sanitised measurement requests, if any were salvageable
     */
    List<SlotMeasurementRequest> validateSlotMeasurements(Labware lw, Collection<SlotMeasurementRequest> sms);

    /**
     * Gets all the problems found.
     * @return a set of strings describing the problems found, if any
     */
    Set<String> compileProblems();
}
