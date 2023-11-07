package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.DnapStudy;

import javax.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

public interface DnapStudyRepo extends CrudRepository<DnapStudy, Integer> {
    Optional<DnapStudy> findByName(String name);

    default DnapStudy getByName(String name) throws EntityNotFoundException {
        return findByName(name).orElseThrow(() -> new EntityNotFoundException("Unknown DNAP study: "+repr(name)));
    }

    Optional<DnapStudy> findBySsId(Integer ssId);

    default DnapStudy getBySsId(Integer ssId) throws EntityNotFoundException {
        return findBySsId(ssId).orElseThrow(() -> new EntityNotFoundException("Unknown Sequencescape study id: "+ssId));
    }

    List<DnapStudy> findAllByEnabled(boolean enabled);
}
