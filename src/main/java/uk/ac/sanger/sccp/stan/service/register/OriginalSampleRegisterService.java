package uk.ac.sanger.sccp.stan.service.register;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.register.OriginalSampleRegisterRequest;
import uk.ac.sanger.sccp.stan.request.register.RegisterResult;
import uk.ac.sanger.sccp.stan.service.ValidationException;

/**
 * Service for registering original samples (biopsies and biomaterial that can later be separated into blocks)
 * @author dr6
 */
public interface OriginalSampleRegisterService {
    /**
     * Validates and registers original samples.
     * @param user the user responsible
     * @param request the request to register new original samples
     * @return the result of the registration
     * @exception ValidationException validation failed
     */
    RegisterResult register(User user, OriginalSampleRegisterRequest request) throws ValidationException;
}
