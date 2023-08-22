package uk.ac.sanger.sccp.stan.service.operation;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.AnalyserRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.service.ValidationException;

/**
 * Service for recording use of analyser
 */
public interface AnalyserService {
    /**
     * Validates and records the analyser request
     * @param user the user responsible for the request
     * @param request the request to perform
     * @return the labware and operations created
     * @exception ValidationException the request failed validation
     */
    OperationResult perform(User user, AnalyserRequest request) throws ValidationException;
}
