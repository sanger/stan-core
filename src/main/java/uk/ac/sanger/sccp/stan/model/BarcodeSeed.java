package uk.ac.sanger.sccp.stan.model;

import javax.persistence.*;

/**
 * A seed to create a barcode.
 * This is returned from the BarcodeSeedRepo to allow the code to generate new unique barcodes.
 * @author dr6
 */
@Entity
public class BarcodeSeed {
    private static final int MIN_NUM_LENGTH = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * Creates a barcode value using this seed, with the given barcode prefix.
     * @param prefix a barcode prefix
     * @return the barcode value with the given prefix using this seed
     */
    public final String toBarcode(String prefix) {
        String hex = Integer.toHexString(this.id).toUpperCase();
        char checksum = calculateChecksum(hex);
        StringBuilder sb = new StringBuilder(prefix.length()+Math.max(MIN_NUM_LENGTH, hex.length())+1);
        sb.append(prefix);
        for (int i = hex.length(); i < MIN_NUM_LENGTH; ++i) {
            sb.append('0');
        }
        sb.append(hex);
        sb.append(checksum);
        return sb.toString();
    }

    // region static methods
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
    // endregion
}
