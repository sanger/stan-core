package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.ReleaseRecipient;
import uk.ac.sanger.sccp.stan.repo.ReleaseRecipientRepo;

import java.util.Optional;

/**
 * Service for admin of release destinations
 * @author dr6
 */
@Service
public class ReleaseRecipientAdminService extends BaseAdminService<ReleaseRecipient, ReleaseRecipientRepo> {
    @Autowired
    public ReleaseRecipientAdminService(ReleaseRecipientRepo repo,
                                        @Qualifier("releaseRecipientValidator") Validator<String> releaseRecipientValidator) {
        super(repo, "Release recipient", "Username", releaseRecipientValidator);
    }

    @Override
    ReleaseRecipient newEntity(String string) {
        return new ReleaseRecipient(null, string);
    }

    @Override
    Optional<ReleaseRecipient> findEntity(ReleaseRecipientRepo repo, String string) {
        return repo.findByUsername(string);
    }
}
