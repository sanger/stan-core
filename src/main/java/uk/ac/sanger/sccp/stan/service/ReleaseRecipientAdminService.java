package uk.ac.sanger.sccp.stan.service;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.ReleaseRecipient;
import uk.ac.sanger.sccp.stan.repo.ReleaseRecipientRepo;

import javax.persistence.EntityExistsException;
import java.util.Optional;

import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

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

    public ReleaseRecipient addNew(String username, String userFullName) {
        username = validateEntity(username);
        return repo.save(new ReleaseRecipient(null, username, userFullName));
    }

    public ReleaseRecipient updateUserFullName(String username, String userFullName) {
        String validatedUserName = validateIdentifier(username);
        ReleaseRecipient recipient = repo.findByUsername(validatedUserName).orElseThrow(() -> new EntityExistsException("Release recipient does not exist: "+validatedUserName));
        if(StringUtils.equals(recipient.getUserFullName(), userFullName)) {
            return recipient;
        }
        recipient.setUserFullName(userFullName);
        return repo.save(recipient);
    }
    @Override
    protected ReleaseRecipient newEntity(String string) {
        return new ReleaseRecipient(null, string);
    }

    @Override
    protected Optional<ReleaseRecipient> findEntity(ReleaseRecipientRepo repo, String string) {
        return repo.findByUsername(string);
    }
}
