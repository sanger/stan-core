package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Tissue;

import javax.persistence.EntityNotFoundException;
import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

public interface TissueRepo extends CrudRepository<Tissue, Integer> {
    List<Tissue> findAllByExternalName(String externalName);

    /**
     * Gets tissues with the given external name.
     * Throws an exception if none are found.
     * @param externalName the external name (external identifier) to find.
     * @return the tissues found with the given external name (non-empty)
     * @exception EntityNotFoundException if no such tissues were found
     */
    default List<Tissue> getAllByExternalName(String externalName) throws EntityNotFoundException {
        List<Tissue> tissues = findAllByExternalName(externalName);
        if (tissues.isEmpty()) {
            throw new EntityNotFoundException("Tissue external name not found: "+repr(externalName));
        }
        return tissues;
    }

    Optional<Tissue> findByDonorIdAndSpatialLocationIdAndMediumIdAndFixativeIdAndReplicate(
            int donorId,
            int spatialLocationId,
            int mediumId,
            int fixativeId,
            String replicate
    );

    List<Tissue> findAllByDonorIdAndSpatialLocationId(int donorId, int spatialLocationId);

    List<Tissue> findByDonorIdAndSpatialLocationIdAndReplicate(int donorId, int spatialLocationId, String replicate);

    List<Tissue> findByDonorId(int donorId);

    List<Tissue> findAllByDonorIdIn(Collection<Integer> donorIds);

    List<Tissue> findAllByExternalNameIn(Collection<String> externalNames);

    @Query("select t from Tissue t join SpatialLocation sl on (t.spatialLocation=sl) where sl.tissueType.id=?1")
    List<Tissue> findByTissueTypeId(int tissueTypeId);

    @Query(value="select max(cast(replicate as unsigned)) from tissue where donor_id=?1 and spatial_location_id=?2", nativeQuery=true)
    Integer findMaxReplicateForDonorIdAndSpatialLocationId(int donorId, int spatialLocationId);

    List<Tissue> findAllByExternalNameLike(String string);
}
