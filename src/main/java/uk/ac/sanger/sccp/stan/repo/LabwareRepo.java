package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Labware;

import javax.persistence.EntityNotFoundException;
import java.util.*;

import static java.util.stream.Collectors.toMap;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

public interface LabwareRepo extends CrudRepository<Labware, Integer> {
    Optional<Labware> findByBarcode(String barcode);
    default Labware getByBarcode(final String barcode) throws EntityNotFoundException {
        return findByBarcode(barcode).orElseThrow(() -> new EntityNotFoundException("No labware found with barcode "+repr(barcode)));
    }
    boolean existsByBarcode(String barcode);

    default Labware getById(final Integer id) throws EntityNotFoundException {
        return findById(id).orElseThrow(() -> new EntityNotFoundException("No labware found with id "+id));
    }

    List<Labware> findByBarcodeIn(Collection<String> barcodes);

    /**
     * Gets an exact sequence of labware identified by barcodes.
     * @param barcodes the barcodes to find
     * @return the matching labware, in the same order as the barcodes, including repetitions
     * @exception EntityNotFoundException some barcodes could not be found
     */
    default List<Labware> getByBarcodeIn(Collection<String> barcodes) {
        if (barcodes.isEmpty()) {
            return List.of();
        }
        List<Labware> foundLabware = findByBarcodeIn(barcodes);
        Map<String, Labware> bcToLabware = foundLabware.stream()
                .collect(toMap(lw -> lw.getBarcode().toUpperCase(), lw -> lw));
        List<String> missing = new ArrayList<>();
        List<Labware> correctLabware = new ArrayList<>(barcodes.size());
        for (String barcode : barcodes) {
            Labware lw = bcToLabware.get(barcode.toUpperCase());
            if (lw==null) {
                missing.add(barcode);
            } else {
                correctLabware.add(lw);
            }
        }
        if (!missing.isEmpty()) {
            throw new EntityNotFoundException("No labware found with barcodes "+missing);
        }
        return correctLabware;
    }

}
