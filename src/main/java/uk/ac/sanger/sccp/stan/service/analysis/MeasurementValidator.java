package uk.ac.sanger.sccp.stan.service.analysis;

import uk.ac.sanger.sccp.stan.request.StringMeasurement;

import java.util.List;
import java.util.Set;

/**
 * A tool for validating measurements and accumulating state and problems for it.
 * Measurements are process in groups (where each group is the measurements for one piece of labware).
 * After every group has been validated, problems are compiled for the whole request.
 * Create a new instance of this for each new request.
 */
public interface MeasurementValidator {
    /**
     * Sanitises the given measurements as the measurements for single labware in an operation.
     * This method accumulates state for compiling problems after all the request's measurements
     * have been validated.
     * Problems may include:<ul>
     *     <li>Missing names or values</li>
     *     <li>Repeated names in the same group</li>
     *     <li>Unexpected measurement names</li>
     *     <li>Invalid measurement values</li>
     *     <li>Unexpected combinations of measurements</li>
     * </ul>
     * @param stringMeasurements the given measurements
     * @return the sanitised measurement, if any
     */
    List<StringMeasurement> validateMeasurements(List<StringMeasurement> stringMeasurements);

    /**
     * Compiles concise problems using the state built up from validating measurements.
     * This should be called *once*, after you've finished calling {@link #validateMeasurements}.
     * @return the compiled problems
     */
    Set<String> compileProblems();
}
