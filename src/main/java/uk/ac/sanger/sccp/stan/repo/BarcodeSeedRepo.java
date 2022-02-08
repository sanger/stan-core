package uk.ac.sanger.sccp.stan.repo;

import com.google.common.collect.Streams;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.BarcodeSeed;

import java.util.List;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

/**
 * A repository for creating barcode seeds, using them to generate new barcodes.
 */
public interface BarcodeSeedRepo extends CrudRepository<BarcodeSeed, Integer> {
    /** The standard prefix for labware barcodes created by Stan. */
    String STAN = "STAN-";

    /**
     * Creates a new barcode with the given prefix.
     * @param prefix the barcode prefix
     * @return the new barcode
     */
    default String createBarcode(String prefix) {
        return save(new BarcodeSeed()).toBarcode(prefix);
    }

    /**
     * Creates multiple new barcodes with the given prefix.
     * @param prefix the barcode prefix
     * @param number the number of barcodes to create
     * @return the new barcodes
     */
    default List<String> createBarcodes(String prefix, int number) {
        List<BarcodeSeed> newSeeds = IntStream.range(0, number).mapToObj(n -> new BarcodeSeed()).collect(toList());
        return Streams.stream(saveAll(newSeeds)).map(bs -> bs.toBarcode(prefix)).collect(toList());
    }

    /**
     * Creates a new barcode with the STAN prefix.
     * @return the new barcode
     * @see #STAN
     */
    default String createStanBarcode() {
        return createBarcode(STAN);
    }
}
