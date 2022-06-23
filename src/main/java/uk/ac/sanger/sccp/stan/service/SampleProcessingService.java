package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.AddExternalIDRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;

public interface SampleProcessingService {
    public OperationResult addExternalID(User user, AddExternalIDRequest request) throws ValidationException;
}
