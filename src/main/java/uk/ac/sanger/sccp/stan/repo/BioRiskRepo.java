package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.BioRisk;
import uk.ac.sanger.sccp.stan.model.Sample;

import javax.persistence.EntityNotFoundException;
import java.util.*;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/** Repo for {@link BioRisk} */
public interface BioRiskRepo extends CrudRepository<BioRisk, Integer> {
    /** Finds the bio risk with the given code, if it exists. */
    Optional<BioRisk> findByCode(String code);

    /**
     * Gets the bio risk with the given code.
     * @param code the code of the bio risk to get
     * @return the bio risk with the given code
     * @exception EntityNotFoundException if no such bio risk exists
     */
    default BioRisk getByCode(String code) throws EntityNotFoundException {
        return findByCode(code).orElseThrow(() -> new EntityNotFoundException("Unknown bio risk code: "+repr(code)));
    }

    List<BioRisk> findAllByCodeIn(Collection<String> codes);

    /** Finds all bio risks matching the given value for enabled. */
    List<BioRisk> findAllByEnabled(boolean enabled);

    @Query(value = "select bio_risk_id from sample_bio_risk where sample_id=?", nativeQuery = true)
    Integer loadBioRiskIdForSampleId(int sampleId);

    @Query(value = "select sample_id, bio_risk_id from sample_bio_risk where sample_id in (?1)", nativeQuery = true)
    int[][] _loadBioRiskIdsForSampleIds(Collection<Integer> sampleIds);

    /** Loads the bio risk (if any) linked to the specified sample */
    default Optional<BioRisk> loadBioRiskForSampleId(int sampleId) {
        Integer bioRiskId = loadBioRiskIdForSampleId(sampleId);
        return bioRiskId == null ? Optional.empty() : findById(bioRiskId);
    }

    /**
     * Loads the bio risk ids linked to the given sample ids
     * @param sampleIds sample ids
     * @return a map of sample id to bio risk id, omitting missing values
     */
    default Map<Integer, Integer> loadBioRiskIdsForSampleIds(Collection<Integer> sampleIds) {
        int[][] sambr = _loadBioRiskIdsForSampleIds(sampleIds);
        if (sambr == null || sambr.length==0) {
            return Map.of();
        }
        return Arrays.stream(sambr)
                .collect(toMap(arr -> arr[0], arr -> arr[1]));
    }

    /**
     * Loads the bio risks linked to the given sample ids
     * @param sampleIds sample ids
     * @return a map of sample id to bio risk, omitting missing values
     */
    default Map<Integer, BioRisk> loadBioRisksForSampleIds(Collection<Integer> sampleIds) {
        int[][] sambr = _loadBioRiskIdsForSampleIds(sampleIds);
        if (sambr==null || sambr.length==0) {
            return Map.of();
        }
        Set<Integer> bioRiskIds = Arrays.stream(sambr)
                .map(arr -> arr[1])
                .collect(toSet());
        Map<Integer, BioRisk> idBioRisks = stream(findAllById(bioRiskIds))
                .collect(inMap(BioRisk::getId));
        return Arrays.stream(sambr)
                .collect(toMap(arr -> arr[0], arr -> idBioRisks.get(arr[1])));
    }

    /**
     * Records the given bio risk id against the given sample id and operation id
     * @param sampleId sample id
     * @param bioRiskId bio risk id
     * @param opId operation id
     */
    @Modifying
    @Query(value = "insert INTO sample_bio_risk (sample_id, bio_risk_id, operation_id) " +
            "values (?, ?, ?)", nativeQuery = true)
    void recordBioRisk(int sampleId, int bioRiskId, Integer opId);

    /** Links the given bio risk to the given sample and operation */
    default void recordBioRisk(Sample sample, BioRisk bioRisk, int opId) {
        recordBioRisk(sample.getId(), bioRisk.getId(), opId);
    }
}
