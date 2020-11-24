package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Sample;

import java.util.OptionalInt;

public interface SampleRepo extends CrudRepository<Sample, Integer> {
    @Query("SELECT MAX(section) FROM Sample WHERE tissue.id=?1")
    Integer _findMaxSectionForTissueId(int tissueId);

    /**
     * Finds the maximum section for particular tissue
     * @param tissueId the id of tissue
     * @return the highest section number of any section with that tissue id
     */
    default OptionalInt findMaxSectionForTissueId(int tissueId) {
        Integer maxInteger = _findMaxSectionForTissueId(tissueId);
        return maxInteger==null ? OptionalInt.empty() : OptionalInt.of(maxInteger);
    }
}
