package uk.ac.sanger.sccp.stan.service.sanitiser;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Sanitiser for numbers including a decimal point, e.g. 12.345
 * @author dr6
 */
public class DecimalSanitiser extends NumberSanitiser<BigDecimal> {
    private final int numDecimalPoints;

    public DecimalSanitiser(String fieldName, int numDecimalPoints, BigDecimal lowerBound, BigDecimal upperBound) {
        super(fieldName, lowerBound, upperBound);
        this.numDecimalPoints = numDecimalPoints;
    }

    @Override
    public BigDecimal parse(String value) throws NumberFormatException, ArithmeticException {
        return new BigDecimal(value).setScale(numDecimalPoints, RoundingMode.UNNECESSARY);
    }
}
