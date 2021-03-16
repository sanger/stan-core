package uk.ac.sanger.sccp.stan.service.register;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.register.RegisterResult;
import uk.ac.sanger.sccp.stan.request.register.SectionRegisterRequest;

/**
 * Service for dealing with section registration.
 * @author dr6
 */
public interface SectionRegisterService {
    /**
     * Performs the registration, creating the indicated labware and samples; recording register operations.
     * @param user the user responsible for the registration
     * @param request the request of what to register
     * @return the result of the registration
     */
    RegisterResult register(User user, SectionRegisterRequest request);
}
