package uk.ac.sanger.sccp.stan.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Helper to validate and sanitise concentration values.
 * A valid value is a positive or negative number with up to two 2 decimal places.
 * The sanitised format is a plain number string (not in scientific notation) with exactly two decimal places.
 * The string representation of the value can be at most 16 characters long, otherwise the value is invalid.
 * @author dr6
 */
public class ConcentrationSanitiser implements Sanitiser<String> {
    private static final int MAX_MEASUREMENT_VALUE_LENGTH = 16;

    @Override
    public String sanitise(String value) {
        if (value==null || value.isEmpty()) {
            return null;
        }
        try {
            BigDecimal bd = new BigDecimal(value);
            String string = bd.setScale(2, RoundingMode.UNNECESSARY).toString();
            if (string.length() > MAX_MEASUREMENT_VALUE_LENGTH) {
                return null;
            }
            return string;
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    @Override
    public String fieldName() {
        return "concentration";
    }
}
