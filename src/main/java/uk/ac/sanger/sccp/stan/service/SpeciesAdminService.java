package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.Species;
import uk.ac.sanger.sccp.stan.repo.SpeciesRepo;
import uk.ac.sanger.sccp.stan.repo.UserRepo;

import java.util.Optional;

/**
 * Service for admin of species
 * @author dr6
 */
@Service
public class SpeciesAdminService extends BaseAdminService<Species, SpeciesRepo> {
    @Autowired
    public SpeciesAdminService(SpeciesRepo repo, UserRepo userRepo,
                               @Qualifier("speciesValidator") Validator<String> speciesValidator,
                               EmailService emailService) {
        super(repo, userRepo, "Species", "Name", speciesValidator, emailService);
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
