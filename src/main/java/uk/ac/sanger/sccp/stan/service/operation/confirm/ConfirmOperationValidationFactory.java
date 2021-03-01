package uk.ac.sanger.sccp.stan.service.operation.confirm;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.confirm.ConfirmOperationRequest;

/**
 * @author dr6
 */
@Component
public class ConfirmOperationValidationFactory {
    private final LabwareRepo labwareRepo;
    private final PlanOperationRepo planOpRepo;
    private final CommentRepo commentRepo;

    @Autowired
    public ConfirmOperationValidationFactory(LabwareRepo labwareRepo, PlanOperationRepo planOpRepo,
                                             CommentRepo commentRepo) {
        this.labwareRepo = labwareRepo;
        this.planOpRepo = planOpRepo;
        this.commentRepo = commentRepo;
    }

    public ConfirmOperationValidation createConfirmOperationValidation(ConfirmOperationRequest request) {
        return new ConfirmOperationValidationImp(request, labwareRepo, planOpRepo, commentRepo);
    }
}
