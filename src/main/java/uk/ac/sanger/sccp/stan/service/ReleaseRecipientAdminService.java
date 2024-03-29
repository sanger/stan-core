package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.ReleaseRecipient;
import uk.ac.sanger.sccp.stan.repo.ReleaseRecipientRepo;

import javax.persistence.EntityNotFoundException;
import java.util.Objects;
import java.util.Optional;

import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * Service for admin of release destinations
 * @author dr6
 */
@Service
public class ReleaseRecipientAdminService extends BaseAdminService<ReleaseRecipient, ReleaseRecipientRepo> {
    @Autowired
    public ReleaseRecipientAdminService(ReleaseRecipientRepo repo,
                                        @Qualifier("releaseRecipientValidator") Validator<String> releaseRecipientValidator,
                                        Transactor transactor, AdminNotifyService notifyService) {
        super(repo, "Release recipient", "Username", releaseRecipientValidator, transactor, notifyService);
    }

    public ReleaseRecipient addNew(String username, String fullName) {
        username = validateEntity(username);
        return repo.save(new ReleaseRecipient(null, username, fullName));
    }

    /**
     * Update the fullname field of an existing recipient
     * @param username username of the recipient
     * @param fullName new fullname of recipient
     * @return updated recipient, if it has been updated
     * @exception EntityNotFoundException if there is no such recipient
     */
    public ReleaseRecipient updateFullName(String username, String fullName) throws EntityNotFoundException {
        if (nullOrEmpty(username)) {
            throw new IllegalArgumentException("Username not specified.");
        }
        ReleaseRecipient recipient = repo.findByUsername(username).orElseThrow(() -> new EntityNotFoundException("Release recipient does not exist: "+repr(username)));
        if (Objects.equals(recipient.getFullName(), fullName)) {
            return recipient;
        }
        recipient.setFullName(fullName);
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
