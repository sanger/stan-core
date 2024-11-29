package uk.ac.sanger.sccp.stan.service;

/**
 * The logic of creating barcodes
 * @author dr6
 */
public class BarcodeUtils {
    public static final String STAN_PREFIX = "STAN-";
    public static final int MIN_NUM_LENGTH = 4;
    private static final String HEX_FORMAT = "%0"+MIN_NUM_LENGTH+"X";

    private BarcodeUtils() {}

    /**
     * Turns the given seed into a barcode with the given prefix.
     * @param prefix prefix for barcode
     * @param seed seed for barcode
     * @return the specified barcode
     */
    public static String barcode(String prefix, int seed) {
        String hex = String.format(HEX_FORMAT, seed);
        char checksum = calculateChecksum(hex);
        return prefix + hex + checksum;
    }

    /**
     * Calculates the checksum for the hexadecimal part of a barcode
     * @param hex the hexadecimal part of the barcode
     * @return the checksum character that will be appended to the barcode
     */
    private static char calculateChecksum(String hex) {
        final int l = hex.length();
        int sum = 0;
        for (int i = 0; i < l; ++i) {
            int v = hexCharToInt(hex.charAt(l-1-i));
            if ((i&1)!=0) {
                v *= 3;
            }
            sum += v;
        }
        return intToHexChar((-sum)&0xf);
    }

    /** Converts a hexadecimal digit (0-9, A-F) to an int */
    private static int hexCharToInt(char ch) {
        if (ch>='0' && ch<='9') return ch-'0';
        if (ch>='A' && ch<='F') return ch-'A'+10;
        throw new IllegalArgumentException("Illegal hex char: "+ch);
    }

    /** Converts a number between 0 and 15 inclusive to a capital hexadecimal digit*/
    private static char intToHexChar(int n) {
        if ((n&0xf)!=n) {
            throw new IllegalArgumentException("Hex char out of range: "+n);
        }
        if (n<10) return (char) ('0'+n);
        return (char) ('A'+n-10);
    }
}
