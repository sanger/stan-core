package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.ReleaseRequest;
import uk.ac.sanger.sccp.stan.request.ReleaseResult;

public interface ReleaseService {
    /**
     * Records a release performed by the given user; commits the transaction; then unstores the barcodes.
     * The transaction is handled inside this service method so that it can be committed before we
     * send the unstore request to storelight.
     * @param user the user responsible for the release
     * @param request the specification of the release
     * @return the result of the release
     */
    ReleaseResult releaseAndUnstore(User user, ReleaseRequest request);
}
