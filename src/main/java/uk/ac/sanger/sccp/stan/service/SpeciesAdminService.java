package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.Species;
import uk.ac.sanger.sccp.stan.repo.SpeciesRepo;

import java.util.Optional;

/**
 * Service for admin of species
 * @author dr6
 */
@Service
public class SpeciesAdminService extends BaseAdminService<Species, SpeciesRepo> {
    @Autowired
    public SpeciesAdminService(SpeciesRepo repo, @Qualifier("speciesValidator") Validator<String> speciesValidator) {
        super(repo, "Species", "Name", speciesValidator);
    }

    @Override
    protected Species newEntity(String string) {
        return new Species(null, string);
    }

    @Override
    protected Optional<Species> findEntity(SpeciesRepo repo, String string) {
        return repo.findByName(string);
    }
}
