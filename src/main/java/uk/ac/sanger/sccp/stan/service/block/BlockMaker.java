package uk.ac.sanger.sccp.stan.service.block;

import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.TissueBlockRequest;

/**
 * Utility for executing a {@link TissueBlockRequest}
 */
public interface BlockMaker {
    /** Executes the request */
    OperationResult record();
}
