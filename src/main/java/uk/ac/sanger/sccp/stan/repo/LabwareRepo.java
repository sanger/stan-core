package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Labware;

import javax.persistence.EntityNotFoundException;
import java.util.Optional;

public interface LabwareRepo extends CrudRepository<Labware, Integer> {
    Optional<Labware> findByBarcode(String barcode);
    default Labware getByBarcode(final String barcode) throws EntityNotFoundException {
        return findByBarcode(barcode).orElseThrow(() -> new EntityNotFoundException("No labware found with barcode "+barcode));
    }
    boolean existsByBarcode(String barcode);
}
