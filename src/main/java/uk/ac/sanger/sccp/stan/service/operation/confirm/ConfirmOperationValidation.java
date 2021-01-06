package uk.ac.sanger.sccp.stan.service.operation.confirm;

import uk.ac.sanger.sccp.stan.request.confirm.ConfirmOperationRequest;

import java.util.Collection;

/**
 * Tool for validating a {@link ConfirmOperationRequest}
 * @author dr6
 */
public interface ConfirmOperationValidation {
    /**
     * Performs validation on the this object's own request, and returns any problems found.
     * @return a collection of strings describing any problems found.
     */
    Collection<String> validate();
}
