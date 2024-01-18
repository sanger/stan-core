package uk.ac.sanger.sccp.stan.service.flag;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.FlagLabwareRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.service.ValidationException;

/**
 * Service for performing {@link FlagLabwareRequest}
 * @author dr6
 */
public interface FlagLabwareService {
    /**
     * Checks and records the given labware flag.
     * @param user the user recording the flag
     * @param request the request to record
     * @return the labware and op recorded
     * @exception ValidationException validation fails
     */
    OperationResult record(User user, FlagLabwareRequest request) throws ValidationException;
}
