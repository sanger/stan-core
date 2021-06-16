package uk.ac.sanger.sccp.stan.service.operation.confirm;

import uk.ac.sanger.sccp.stan.request.confirm.ConfirmSectionRequest;

public interface ConfirmSectionValidationService {
    /**
     * Finds problems with the given request
     * @param request the request to confirm
     * @return an object describing the result of the validation
     */
    ConfirmSectionValidation validate(ConfirmSectionRequest request);
}
