package uk.ac.sanger.sccp.stan.service.sanitiser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * Sanitiser for numbers including a decimal point, e.g. 12.345
 * @author dr6
 */
public class DecimalSanitiser extends NumberSanitiser<BigDecimal> {
    private final int numDecimalPlaces;
    private final Pattern tooManyDecimalPlacesPattern;

    public DecimalSanitiser(String fieldName, int numDecimalPlaces, BigDecimal lowerBound, BigDecimal upperBound) {
        super(fieldName, lowerBound, upperBound);
        this.numDecimalPlaces = numDecimalPlaces;
        this.tooManyDecimalPlacesPattern = Pattern.compile("[-+]?[0-9]\\.[0-9]{"+numDecimalPlaces+"}0*[1-9][0-9]*$");
    }

    @Override
    public BigDecimal parse(String value) throws NumberFormatException, ArithmeticException {
        return new BigDecimal(value).setScale(numDecimalPlaces, RoundingMode.UNNECESSARY);
    }

    @Override
    public String errorMessage(String value) {
        if (value!=null && tooManyDecimalPlacesPattern.matcher(value).matches()) {
            String expectation = switch (numDecimalPlaces) {
                case 0 -> ", expected no decimal places: ";
                case 1 -> ", expected only 1 decimal place: ";
                default -> ", expected up to "+ numDecimalPlaces +" decimal places: ";
            };
            return "Invalid value for " + fieldName + expectation + value;
        }
        return super.errorMessage(value);
    }
}
