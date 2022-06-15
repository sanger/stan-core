package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Tissue;

import javax.persistence.EntityNotFoundException;
import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

public interface TissueRepo extends CrudRepository<Tissue, Integer> {
    Optional<Tissue> findByExternalName(String externalName);
    Optional<Tissue> findByDonorIdAndSpatialLocationIdAndMediumIdAndFixativeIdAndReplicate(
            int donorId,
            int spatialLocationId,
            int mediumId,
            int fixativeId,
            String replicate
    );

    List<Tissue> findAllByDonorIdAndSpatialLocationId(int donorId, int spatialLocationId);

    default Tissue getByExternalName(String externalName) throws EntityNotFoundException {
        return findByExternalName(externalName).orElseThrow(
                () -> new EntityNotFoundException("Tissue external name not found: "+repr(externalName))
        );
    }

    List<Tissue> findByDonorId(int donorId);

    List<Tissue> findAllByExternalNameIn(Collection<String> externalNames);

    @Query("select t from Tissue t join SpatialLocation sl on (t.spatialLocation=sl) where sl.tissueType.id=?1")
    List<Tissue> findByTissueTypeId(int tissueTypeId);
}
