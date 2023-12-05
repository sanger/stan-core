package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.ReactivateLabware;

import java.util.List;

/**
 * Service to reactivate destroyed/discarded labware
 * @author dr6
 */
public interface ReactivateService {
    OperationResult reactivate(User user, List<ReactivateLabware> items) throws ValidationException;
}
