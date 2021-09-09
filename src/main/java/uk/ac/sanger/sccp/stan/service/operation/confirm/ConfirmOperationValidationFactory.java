package uk.ac.sanger.sccp.stan.service.operation.confirm;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.ac.sanger.sccp.stan.repo.LabwareRepo;
import uk.ac.sanger.sccp.stan.repo.PlanOperationRepo;
import uk.ac.sanger.sccp.stan.request.confirm.ConfirmOperationRequest;
import uk.ac.sanger.sccp.stan.service.CommentValidationService;

/**
 * @author dr6
 */
@Component
public class ConfirmOperationValidationFactory {
    private final LabwareRepo labwareRepo;
    private final PlanOperationRepo planOpRepo;
    private final CommentValidationService commentValidationService;

    @Autowired
    public ConfirmOperationValidationFactory(LabwareRepo labwareRepo, PlanOperationRepo planOpRepo,
                                             CommentValidationService commentValidationService) {
        this.labwareRepo = labwareRepo;
        this.planOpRepo = planOpRepo;
        this.commentValidationService = commentValidationService;
    }

    public ConfirmOperationValidation createConfirmOperationValidation(ConfirmOperationRequest request) {
        return new ConfirmOperationValidationImp(request, labwareRepo, planOpRepo, commentValidationService);
    }
}
