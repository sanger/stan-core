package uk.ac.sanger.sccp.stan.service.analysis;

import uk.ac.sanger.sccp.stan.request.StringMeasurement;
import uk.ac.sanger.sccp.stan.service.Sanitiser;

import java.util.*;

/**
 * Helper to validate measurements requested as part of an analysis operation
 * @author dr6
 */
public class AnalysisMeasurementValidator implements MeasurementValidator {

    public enum AnalysisType { RIN, DV200 }

    /** The name of a measurement that we are expecting to validate. */
    public static final String
            RIN_VALUE_NAME = "RIN",
            DV200_VALUE_NAME = "DV200",
            DV200_LOWER_NAME = "DV200 lower",
            DV200_UPPER_NAME = "DV200 upper";

    private final AnalysisType analysisType;
    private final Sanitiser<String> sanitiser;
    private final Collection<String> allowedMeasurementNames;

    private final Set<String> invalidMeasurementNames = new LinkedHashSet<>();
    private final Set<String> problems = new LinkedHashSet<>();
    private boolean anyNameNull = false;
    private boolean anyValueNull = false;

    /**
     * Creates a validator to validate the specified analysis type.
     * @param analysisType the analysis type
     * @param sanitiser the sanitiser to use on the measurement values
     * @param allowedMeasurementNames the permissible measurement names
     */
    public AnalysisMeasurementValidator(AnalysisType analysisType, Sanitiser<String> sanitiser,
                                        Collection<String> allowedMeasurementNames) {
        this.analysisType = analysisType;
        this.sanitiser = sanitiser;
        this.allowedMeasurementNames = allowedMeasurementNames;
    }

    @Override
    public List<StringMeasurement> validateMeasurements(List<StringMeasurement> sms) {
        if (sms==null || sms.isEmpty()) {
            return List.of();
        }
        List<StringMeasurement> sanSms = new ArrayList<>(sms.size());
        for (StringMeasurement sm : sms) {
            String name = sm.getName();
            if (name == null || name.isEmpty()) {
                anyNameNull = true;
                continue;
            }
            String sanName = sanitiseName(name);
            if (sanName == null) {
                invalidMeasurementNames.add(name);
                continue;
            }
            if (sm.getValue()==null) {
                anyValueNull = true;
                continue;
            }
            String sanValue = sanitiser.sanitise(problems, sm.getValue());
            if (sanValue != null) {
                sanSms.add(new StringMeasurement(sanName, sanValue));
            }
        }
        checkCombinations(sanSms);
        return sanSms;
    }

    /**
     * Checks if the given string matches one of the expected measurement names.
     * @param name the given name
     * @return the matching measurement name, if there is one; otherwise null
     */
    public String sanitiseName(String name) {
        return this.allowedMeasurementNames.stream()
                .filter(s -> s.equalsIgnoreCase(name))
                .findAny()
                .orElse(null);
    }

    /**
     * Checks the group of measurements given for invalid combinations,
     * such as a measurement given twice, or a DV200 lower bound given without an upper bound.
     * The problems found will be available via {@link #compileProblems}.
     * @param sms a group of measurements all happening on the same piece of labware
     */
    public void checkCombinations(Collection<StringMeasurement> sms) {
        if (sms.isEmpty() || sms.size()==1 && analysisType != AnalysisType.DV200) {
            return;
        }

        Set<String> seen = new HashSet<>(sms.size());
        Set<String> repeated = new LinkedHashSet<>();
        for (StringMeasurement sm : sms) {
            String name = sm.getName();
            if (!seen.add(name)) {
                repeated.add(name);
            }
        }
        if (!repeated.isEmpty()) {
            problems.add(String.format("Measurement%s given multiple times for the same labware: %s",
                    repeated.size()==1 ? "" : "s",
                    repeated));
        }
        if (analysisType==AnalysisType.DV200) {
            if (seen.contains(DV200_LOWER_NAME) != seen.contains(DV200_UPPER_NAME)) {
                problems.add((seen.contains(DV200_UPPER_NAME) ? "DV200 upper bound given without lower bound."
                        : "DV200 lower bound given without upper bound."));
            } else if (seen.contains(DV200_LOWER_NAME) && seen.contains(DV200_VALUE_NAME)) {
                problems.add("Bounds and actual value both given for DV200.");
            }
        }
    }

    @Override
    public Set<String> compileProblems() {
        if (anyNameNull) {
            problems.add("Measurement name missing.");
        }
        if (anyValueNull) {
            problems.add("Measurement value missing.");
        }
        if (!invalidMeasurementNames.isEmpty()) {
            problems.add("Invalid measurement types given for "+this.analysisType+" analysis: "+invalidMeasurementNames);
        }
        return this.problems;
    }

    /**
     * Gets the current set of problems found.
     * If {@link #compileProblems} has not been called yet, this will be incomplete.
     * @return a set of problems found
     */
    public Set<String> getProblems() {
        return this.problems;
    }

    /** The set of invalid measurement names found (these will be compiled into problems). */
    public Set<String> getInvalidMeasurementNames() {
        return this.invalidMeasurementNames;
    }

    public AnalysisType getAnalysisType() {
        return this.analysisType;
    }

    public Sanitiser<String> getSanitiser() {
        return this.sanitiser;
    }

    public Collection<String> getAllowedMeasurementNames() {
        return this.allowedMeasurementNames;
    }

    public void setAnyNameNull(boolean anyNameNull) {
        this.anyNameNull = anyNameNull;
    }

    public void setAnyValueNull(boolean anyValueNull) {
        this.anyValueNull = anyValueNull;
    }
}
