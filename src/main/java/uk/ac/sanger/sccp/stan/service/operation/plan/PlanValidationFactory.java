package uk.ac.sanger.sccp.stan.service.operation.plan;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.plan.PlanRequest;
import uk.ac.sanger.sccp.stan.service.Validator;

/**
 * @author dr6
 */
@Component
public class PlanValidationFactory {
    private final LabwareRepo lwRepo;
    private final LabwareTypeRepo ltRepo;
    private final OperationTypeRepo opTypeRepo;
    private final Validator<String> prebarcodeValidator;
    private final Validator<String> lotNumberValidator;

    @Autowired
    public PlanValidationFactory(LabwareRepo lwRepo, LabwareTypeRepo ltRepo, OperationTypeRepo opTypeRepo,
                                 @Qualifier("visiumLPBarcodeValidator") Validator<String> prebarcodeValidator,
                                 @Qualifier("lotNumberValidator") Validator<String> lotNumberValidator) {
        this.lwRepo = lwRepo;
        this.ltRepo = ltRepo;
        this.opTypeRepo = opTypeRepo;
        this.prebarcodeValidator = prebarcodeValidator;
        this.lotNumberValidator = lotNumberValidator;
    }

    public PlanValidation createPlanValidation(PlanRequest request) {
        return new PlanValidationImp(request, lwRepo, ltRepo, opTypeRepo, prebarcodeValidator, lotNumberValidator);
    }
}
