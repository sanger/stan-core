package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Equipment;

import javax.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;

/**
 * Repo for dealing with equipment
 * @author dr6
 */
public interface EquipmentRepo extends CrudRepository<Equipment, Integer> {
    List<Equipment> findAllByCategory(String category);
    List<Equipment> findAllByCategoryAndEnabled(String category, boolean enabled);
    List<Equipment> findAllByEnabled(boolean enabled);

    Optional<Equipment> findByCategoryAndName(String category, String text);

    default Equipment getById(int id) throws EntityNotFoundException {
        return findById(id).orElseThrow(() -> new EntityNotFoundException("No equipment found with id "+id));
    }
}
