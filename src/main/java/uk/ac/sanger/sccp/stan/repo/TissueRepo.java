package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Tissue;

import java.util.Optional;

public interface TissueRepo extends CrudRepository<Tissue, Integer> {
    Optional<Tissue> findByExternalNameAndReplicate(String externalIdentifier, int replicate);
}
