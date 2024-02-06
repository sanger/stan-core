package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.ReleaseDestination;
import uk.ac.sanger.sccp.stan.repo.ReleaseDestinationRepo;
import uk.ac.sanger.sccp.stan.repo.UserRepo;

import java.util.Optional;

/**
 * Service for admin of release destinations
 * @author dr6
 */
@Service
public class ReleaseDestinationAdminService extends BaseAdminService<ReleaseDestination, ReleaseDestinationRepo> {
    @Autowired
    public ReleaseDestinationAdminService(ReleaseDestinationRepo repo, UserRepo userRepo,
                                          @Qualifier("releaseDestinationValidator") Validator<String> releaseDestinationValidator,
                                          EmailService emailService) {
        super(repo, userRepo, "Release destination", "Name", releaseDestinationValidator, emailService);
    }

    @Override
    protected ReleaseDestination newEntity(String string) {
        return new ReleaseDestination(null, string);
    }

    @Override
    protected Optional<ReleaseDestination> findEntity(ReleaseDestinationRepo repo, String string) {
        return repo.findByName(string);
    }
}
