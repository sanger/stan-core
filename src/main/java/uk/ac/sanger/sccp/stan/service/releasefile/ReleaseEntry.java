package uk.ac.sanger.sccp.stan.service.releasefile;

/**
 * @author dr6
 */
public class ReleaseEntry {
    private String barcode;

    public ReleaseEntry(String barcode) {
        this.barcode = barcode;
    }

    public String getBarcode() {
        return this.barcode;
    }
}
