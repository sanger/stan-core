package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.CostCode;

import javax.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * @author dr6
 */
public interface CostCodeRepo extends CrudRepository<CostCode, Integer> {
    Optional<CostCode> findByCode(String code);

    default CostCode getByCode(String code) throws EntityNotFoundException {
        return findByCode(code).orElseThrow(() -> new EntityNotFoundException("Unknown cost code: "+repr(code)));
    }

    List<CostCode> findAllByEnabled(boolean enabled);
}
