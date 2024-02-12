package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.DestructionReason;
import uk.ac.sanger.sccp.stan.repo.DestructionReasonRepo;

import java.util.Optional;

/**
 * Service for admin of destruction reasons
 * @author dr6
 */
@Service
public class DestructionReasonAdminService extends BaseAdminService<DestructionReason, DestructionReasonRepo> {
    @Autowired
    public DestructionReasonAdminService(DestructionReasonRepo destructionReasonRepo,
                                         @Qualifier("destructionReasonValidator") Validator<String> destructionReasonValidator,
                                         Transactor transactor, AdminNotifyService notifyService) {
        super(destructionReasonRepo, "Destruction reason", "Text",
                destructionReasonValidator, transactor, notifyService);
    }

    @Override
    protected DestructionReason newEntity(String string) {
        return new DestructionReason(null, string);
    }

    @Override
    protected Optional<DestructionReason> findEntity(DestructionReasonRepo repo, String string) {
        return repo.findByText(string);
    }
}
