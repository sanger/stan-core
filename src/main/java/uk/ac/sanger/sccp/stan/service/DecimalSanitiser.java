package uk.ac.sanger.sccp.stan.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * @author dr6
 */
public class DecimalSanitiser implements Sanitiser<String> {
    private static final int MAX_MEASUREMENT_VALUE_LENGTH = 16;
    private final String fieldName;
    private final int numDecimalPoints;
    private final BigDecimal lowerBound;
    private final BigDecimal upperBound;

    public DecimalSanitiser(String fieldName, int numDecimalPoints, BigDecimal lowerBound, BigDecimal upperBound) {
        this.fieldName = fieldName;
        this.numDecimalPoints = numDecimalPoints;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    @Override
    public String sanitise(Collection<String> problems, String value) {
        if (value==null || value.isEmpty()) {
            if (problems!=null) {
                problems.add("No value given for "+fieldName);
            }
            return null;
        }
        BigDecimal bd;
        try {
            bd = new BigDecimal(value).setScale(numDecimalPoints, RoundingMode.UNNECESSARY);
        } catch (NumberFormatException | ArithmeticException e) {
            if (problems!=null) {
                problems.add("Invalid value for "+fieldName+": " + repr(value));
            }
            return null;
        }
        if (lowerBound!=null && bd.compareTo(lowerBound) < 0
                || upperBound!=null && bd.compareTo(upperBound) > 0) {
            if (problems!=null) {
                problems.add("Value outside the expected bounds for "+fieldName+": "+repr(value));
            }
            return null;
        }
        String sanitisedValue = bd.toString();
        if (sanitisedValue.length() > MAX_MEASUREMENT_VALUE_LENGTH) {
            if (problems!=null) {
                problems.add("Sanitised value too long for "+fieldName + ": "+ repr(sanitisedValue));
            }
            return null;
        }
        return sanitisedValue;
    }
}
