package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.ReleaseDestination;
import uk.ac.sanger.sccp.stan.repo.ReleaseDestinationRepo;

import java.util.Optional;

/**
 * Service for admin of release destinations
 * @author dr6
 */
@Service
public class ReleaseDestinationAdminService extends BaseAdminService<ReleaseDestination, ReleaseDestinationRepo> {
    @Autowired
    public ReleaseDestinationAdminService(ReleaseDestinationRepo repo,
                                          @Qualifier("releaseDestinationValidator") Validator<String> releaseDestinationValidator) {
        super(repo, "Release destination", "Name", releaseDestinationValidator);
    }

    @Override
    ReleaseDestination newEntity(String string) {
        return new ReleaseDestination(null, string);
    }

    @Override
    Optional<ReleaseDestination> findEntity(ReleaseDestinationRepo repo, String string) {
        return repo.findByName(string);
    }
}
