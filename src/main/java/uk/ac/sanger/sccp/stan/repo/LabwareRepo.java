package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Labware;
import uk.ac.sanger.sccp.utils.UCMap;

import javax.persistence.EntityNotFoundException;
import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

public interface LabwareRepo extends CrudRepository<Labware, Integer> {
    Optional<Labware> findByBarcode(String barcode);

    default Labware getByBarcode(final String barcode) throws EntityNotFoundException {
        return findByBarcode(barcode).orElseThrow(() -> new EntityNotFoundException("No labware found with barcode "+repr(barcode)));
    }

    boolean existsByBarcode(String barcode);

    boolean existsByExternalBarcode(String externalBarcode);

    @Query("select barcode from Labware where barcode in (?1)")
    Set<String> findBarcodesByBarcodeIn(Collection<String> barcodes);

    default Labware getById(final Integer id) throws EntityNotFoundException {
        return findById(id).orElseThrow(() -> new EntityNotFoundException("No labware found with id "+id));
    }

    List<Labware> findByBarcodeIn(Collection<String> barcodes);

    List<Labware> findByExternalBarcodeIn(Collection<String> barcodes);

    List<Labware> findAllByIdIn(Collection<Integer> ids);

    /**
     * Gets an exact sequence of labware identified by barcodes.
     * @param barcodes the barcodes to find
     * @return the matching labware, in the same order as the barcodes, including repetitions
     * @exception EntityNotFoundException some barcodes could not be found
     */
    default List<Labware> getByBarcodeIn(Collection<String> barcodes) throws EntityNotFoundException {
        return RepoUtils.getAllByField(this::findByBarcodeIn, barcodes, Labware::getBarcode,
                "No labware found with barcode{s}: ", String::toUpperCase);
    }

    /**
     * Gets labware by barcodes
     * @param barcodes the barcodes to find
     * @return a UCMap of barcodes to labware
     * @exception EntityNotFoundException any barcodes are not found
     */
    default UCMap<Labware> getMapByBarcodeIn(Collection<String> barcodes) throws EntityNotFoundException {
        return RepoUtils.getUCMapByField(this::findByBarcodeIn, barcodes, Labware::getBarcode,
                "No labware found with barcode{s}: ");
    }

    @Query(value = "SELECT DISTINCT slot.labware_id FROM slot_sample ss JOIN slot ON (ss.slot_id=slot.id) " +
            "WHERE ss.sample_id IN (?1)", nativeQuery = true)
    Set<Integer> findAllLabwareIdsContainingSampleIds(Collection<Integer> sampleIds);
}
