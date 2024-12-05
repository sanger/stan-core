package uk.ac.sanger.sccp.stan.service.operation.plan;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.plan.PlanRequest;
import uk.ac.sanger.sccp.stan.service.Validator;
import uk.ac.sanger.sccp.stan.service.sanitiser.Sanitiser;

/**
 * @author dr6
 */
@Component
public class PlanValidationFactory {
    private final LabwareRepo lwRepo;
    private final LabwareTypeRepo ltRepo;
    private final OperationTypeRepo opTypeRepo;
    private final Validator<String> visiumBarcodeValidator;
    private final Validator<String> xeniumBarcodeValidator;
    private final Validator<String> lotNumberValidator;
    private final Sanitiser<String> thicknessSanitiser;

    @Autowired
    public PlanValidationFactory(LabwareRepo lwRepo, LabwareTypeRepo ltRepo, OperationTypeRepo opTypeRepo,
                                 @Qualifier("visiumLPBarcodeValidator") Validator<String> visiumBarcodeValidator,
                                 @Qualifier("xeniumBarcodeValidator") Validator<String> xeniumBarcodeValidator,
                                 @Qualifier("lotNumberValidator") Validator<String> lotNumberValidator,
                                 @Qualifier("thicknessSanitiser") Sanitiser<String> thicknessSanitiser) {
        this.lwRepo = lwRepo;
        this.ltRepo = ltRepo;
        this.opTypeRepo = opTypeRepo;
        this.visiumBarcodeValidator = visiumBarcodeValidator;
        this.xeniumBarcodeValidator = xeniumBarcodeValidator;
        this.lotNumberValidator = lotNumberValidator;
        this.thicknessSanitiser = thicknessSanitiser;
    }

    public PlanValidation createPlanValidation(PlanRequest request) {
        return new PlanValidationImp(request, lwRepo, ltRepo, opTypeRepo,
                visiumBarcodeValidator, xeniumBarcodeValidator, lotNumberValidator,
                thicknessSanitiser);
    }
}
