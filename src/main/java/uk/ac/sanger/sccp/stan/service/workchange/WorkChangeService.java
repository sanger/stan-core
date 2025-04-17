package uk.ac.sanger.sccp.stan.service.workchange;

import uk.ac.sanger.sccp.stan.model.Operation;
import uk.ac.sanger.sccp.stan.request.OpWorkRequest;
import uk.ac.sanger.sccp.stan.service.ValidationException;

import java.util.List;

/** Service for changing work linked to prior events */
public interface WorkChangeService {
    /** Validate and perform the request. */
    List<Operation> perform(OpWorkRequest request) throws ValidationException;
}
