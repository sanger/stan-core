package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Tissue;

import javax.persistence.EntityNotFoundException;
import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

public interface TissueRepo extends CrudRepository<Tissue, Integer> {
    List<Tissue> findAllByIdIn(Collection<Integer> tissueIds);

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

    /**
     * Gets all tissue ids related to the given tissue ids
     * @param tissueIds starting tissue ids
     * @return all related tissue ids
     */
    default Set<Integer> findRelatedTissueIds(Collection<Integer> tissueIds) {
        // Find all tissue ancestors of the specified tissues,
        // and then all descendants of those ancestors
        Set<Integer> ancestorIds = new HashSet<>();
        Collection<Integer> newParentIds = tissueIds;
        while (!newParentIds.isEmpty()) {
            ancestorIds.addAll(newParentIds);
            newParentIds = findParentIds(newParentIds);
            newParentIds.removeAll(ancestorIds);
        }
        Set<Integer> descendentIds = new HashSet<>();
        Set<Integer> newChildIds = ancestorIds;
        while (!newChildIds.isEmpty()) {
            descendentIds.addAll(newChildIds);
            newChildIds = findChildIds(newChildIds);
            newChildIds.removeAll(descendentIds);
        }
        return descendentIds;
    }

    @Query(value="select distinct parentId from Tissue where id in (?1) and parentId is not null")
    Set<Integer> findParentIds(Collection<Integer> tissueIds);
    @Query(value="select id from Tissue where parentId in (?1)")
    Set<Integer> findChildIds(Collection<Integer> tissueIds);
}
