package uk.ac.sanger.sccp.stan.model;

import uk.ac.sanger.sccp.stan.service.BarcodeUtils;

import javax.persistence.*;

/**
 * A seed to create a barcode.
 * This is returned from the BarcodeSeedRepo to allow the code to generate new unique barcodes.
 * @author dr6
 */
@Entity
public class BarcodeSeed {

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
        return BarcodeUtils.barcode(prefix, this.id);
    }

}
