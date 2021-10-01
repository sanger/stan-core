package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.ExtractResultRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;

/**
 * Service for recording the result for an extract op.
 */
public interface ExtractResultService {
    OperationResult recordExtractResult(User user, ExtractResultRequest request) throws ValidationException;
}
