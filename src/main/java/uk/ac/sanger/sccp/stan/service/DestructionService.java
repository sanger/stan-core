package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.DestroyRequest;
import uk.ac.sanger.sccp.stan.request.DestroyResult;

/**
 * Service for dealing with destroying labware.
 */
public interface DestructionService {
    /**
     * Destroy labware and unstore it.
     * The updating of labware and the creating of destructions happens in a transaction.
     * The unstoring happens post-transaction, and is not guaranteed.
     * @param user the user responsible
     * @param request the request of what to destroy
     * @return the result of the destructions
     */
    DestroyResult destroyAndUnstore(User user, DestroyRequest request);
}
