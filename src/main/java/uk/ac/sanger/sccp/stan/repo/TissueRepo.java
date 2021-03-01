package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Tissue;

import javax.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

public interface TissueRepo extends CrudRepository<Tissue, Integer> {
    Optional<Tissue> findByExternalName(String externalName);
    Optional<Tissue> findByDonorIdAndSpatialLocationIdAndMediumIdAndFixativeIdAndReplicate(
            int donorId,
            int spatialLocationId,
            int mediumId,
            int fixativeId,
            int replicate
    );

    default Tissue getByExternalName(String externalName) throws EntityNotFoundException {
        return findByExternalName(externalName).orElseThrow(
                () -> new EntityNotFoundException("Tissue external name not found: "+repr(externalName))
        );
    }

    List<Tissue> findByDonorId(int donorId);
}
