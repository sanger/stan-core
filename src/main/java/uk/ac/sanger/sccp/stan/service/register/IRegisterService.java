package uk.ac.sanger.sccp.stan.service.register;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.register.RegisterResult;
import uk.ac.sanger.sccp.stan.service.ValidationException;

/**
 * @author dr6
 */
@FunctionalInterface
public interface IRegisterService<RequestType> {
    /**
     * Performs the registration, creating the indicated labware and samples; recording register operations.
     * @param user the user responsible for the registration
     * @param request the request of what to register
     * @return the result of the registration
     * @exception ValidationException the request was found to be invalid
     */
    RegisterResult register(User user, RequestType request) throws ValidationException;
}
