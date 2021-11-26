package uk.ac.sanger.sccp.stan.service.sanitiser;

/**
 * Sanitiser for integers (falling within the normal 32 bit signed integer range).
 * @author dr6
 */
public class IntSanitiser extends NumberSanitiser<Integer> {
    public IntSanitiser(String fieldName, Integer lowerBound, Integer upperBound) {
        super(fieldName, lowerBound, upperBound);
    }

    @Override
    public Integer parse(String value) throws NumberFormatException {
        return Integer.valueOf(value);
    }
}
