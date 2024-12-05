package uk.ac.sanger.sccp.stan.service.sanitiser;

import java.util.Collection;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * Abstract base class for sanitising numerical values with optional lower and upper bounds.
 * @author dr6
 */
public abstract class NumberSanitiser<N extends Comparable<N>> implements Sanitiser<String> {
    public static final int MAX_MEASUREMENT_VALUE_LENGTH = 16;

    protected final String fieldName;
    private final N lowerBound;
    private final N upperBound;

    public NumberSanitiser(String fieldName, N lowerBound, N upperBound) {
        this.fieldName = fieldName;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    public abstract N parse(String value) throws NumberFormatException, ArithmeticException;

    public String toStringValue(N num) {
        return num.toString();
    }

    /**
     * Supplies the error message explaining the invalid value
     * @param value the invalid value
     * @return a problem message
     */
    public String errorMessage(String value) {
        return "Invalid value for "+fieldName+": " + repr(value);
    }

    @Override
    public String sanitise(Collection<String> problems, String value) {
        if (value==null || value.isEmpty()) {
            if (problems!=null) {
                problems.add("No value given for "+fieldName);
            }
            return null;
        }
        N number;
        try {
            number = parse(value);
        } catch (RuntimeException e) {
            if (problems!=null) {
                String problem = errorMessage(value);
                if (problem!=null) {
                    problems.add(problem);
                }
            }
            return null;
        }
        if (number==null) {
            return null;
        }
        if (lowerBound!=null && number.compareTo(lowerBound) < 0
                || upperBound!=null && number.compareTo(upperBound) > 0) {
            if (problems!=null) {
                problems.add("Value outside the expected bounds for "+fieldName+": "+value);
            }
            return null;
        }
        String sanitisedValue = toStringValue(number);
        if (sanitisedValue.length() > MAX_MEASUREMENT_VALUE_LENGTH) {
            if (problems!=null) {
                problems.add("Sanitised value too long for "+fieldName + ": "+ repr(sanitisedValue));
            }
            return null;
        }
        return sanitisedValue;
    }
}
