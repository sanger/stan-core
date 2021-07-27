package uk.ac.sanger.sccp.stan.service.operation.plan;

import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.request.PlanData;
import uk.ac.sanger.sccp.stan.request.plan.PlanRequest;
import uk.ac.sanger.sccp.stan.request.plan.PlanResult;
import uk.ac.sanger.sccp.stan.service.ValidationException;

public interface PlanService {
    /**
     * Attempts to record the plan as specified.
     * @param user user performing the plan
     * @param request description of the plan
     * @return a result including the planned operations and new labware
     * @exception ValidationException a description of validation problems
     */
    PlanResult recordPlan(User user, PlanRequest request) throws ValidationException;

    /**
     * Gets the plan data for a destination labware barcode.
     * @param barcode the barcode of a labware for which a plan has been recorded
     * @return the plan data for that barcode
     */
    PlanData getPlanData(String barcode);
}
