package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Sample;

import javax.persistence.EntityNotFoundException;
import java.util.*;

import static java.util.stream.Collectors.toMap;

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
     * Gets the samples matching the corresponding ids.
     * @param ids the ids to find
     * @return the samples in the order of the corresponding ids
     * @exception EntityNotFoundException any ids were not found
     */
    default List<Sample> getAllByIdIn(Collection<Integer> ids) throws EntityNotFoundException {
        Map<Integer, Sample> sampleIdMap = findAllByIdIn(ids).stream()
                .collect(toMap(Sample::getId, r -> r));

        LinkedHashSet<Integer> missing = new LinkedHashSet<>();
        List<Sample> samples = new ArrayList<>(ids.size());
        for (Integer id : ids) {
            Sample sample = sampleIdMap.get(id);
            if (sample==null) {
                missing.add(id);
            } else {
                samples.add(sample);
            }
        }
        if (!missing.isEmpty()) {
            throw new EntityNotFoundException(String.format("Unknown sample %s: %s",
                    missing.size() == 1 ? "id" : "ids", missing));
        }
        return samples;
    }
}
