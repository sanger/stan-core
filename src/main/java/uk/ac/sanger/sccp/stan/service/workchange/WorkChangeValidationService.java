package uk.ac.sanger.sccp.stan.service.workchange;

import uk.ac.sanger.sccp.stan.request.OpWorkRequest;
import uk.ac.sanger.sccp.stan.service.ValidationException;

/**
 * Service for validating work change requests
 * @author dr6
 */
public interface WorkChangeValidationService {
    /**
     * Validates the request and returns relevant data loaded.
     * Note that the operations returned exclude ones already linked to the indicated work.
     * @param request the request to link work to existing ops
     * @return the work and ops indicated by the request
     * @exception ValidationException if the validation fails
     */
    WorkChangeData validate(OpWorkRequest request) throws ValidationException;
}
