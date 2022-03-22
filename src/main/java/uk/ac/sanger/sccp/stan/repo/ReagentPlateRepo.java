package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentPlate;

import javax.persistence.EntityNotFoundException;
import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

public interface ReagentPlateRepo extends CrudRepository<ReagentPlate, Integer> {
    Optional<ReagentPlate> findByBarcode(String barcode);

    default ReagentPlate getByBarcode(String barcode) throws EntityNotFoundException {
        return findByBarcode(barcode).orElseThrow(() -> new EntityNotFoundException("Unknown reagent plate barcode: "+repr(barcode)));
    }

    List<ReagentPlate> findAllByBarcodeIn(Collection<String> barcodes);
}
