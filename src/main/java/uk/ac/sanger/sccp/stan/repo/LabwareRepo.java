package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Labware;

import java.util.Optional;

public interface LabwareRepo extends CrudRepository<Labware, Integer> {
    Optional<Labware> findByBarcode(String barcode);
}
