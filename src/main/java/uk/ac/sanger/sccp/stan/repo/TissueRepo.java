package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Tissue;

import java.util.List;
import java.util.Optional;

public interface TissueRepo extends CrudRepository<Tissue, Integer> {
    Optional<Tissue> findByExternalName(String externalName);
    List<Tissue> findByDonorIdAndSpatialLocationIdAndReplicate(int donorId, int spatialLocationId, int replicate);
}
