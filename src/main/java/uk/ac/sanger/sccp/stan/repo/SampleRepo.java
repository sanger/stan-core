package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Sample;

import javax.persistence.EntityNotFoundException;
import java.util.*;

public interface SampleRepo extends CrudRepository<Sample, Integer> {
    @Query("SELECT MAX(section) FROM Sample WHERE tissue.id=?1")
    Integer _findMaxSectionForTissueId(int tissueId);

    List<Sample> findAllByIdIn(Collection<Integer> ids);

    List<Sample> findAllByTissueIdIn(Collection<Integer> tissueIds);

    /**
     * Finds the maximum section for particular tissue
     *
     * @param tissueId the id of tissue
     * @return the highest section number of any section with that tissue id
     * @deprecated no current use case
     */
    default OptionalInt findMaxSectionForTissueId(int tissueId) {
        Integer maxInteger = _findMaxSectionForTissueId(tissueId);
        return maxInteger == null ? OptionalInt.empty() : OptionalInt.of(maxInteger);
    }

    /**
     * Gets the samples with the given ids
     * @param ids the ids to find
     * @return a map of id to sample
     * @exception EntityNotFoundException any ids were not found
     */
    default Map<Integer, Sample> getMapByIdIn(Collection<Integer> ids) throws EntityNotFoundException {
        return RepoUtils.getMapByField(this::findAllByIdIn, ids, Sample::getId,
                "Unknown sample ID{s}: ");
    }
}
