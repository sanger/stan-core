package uk.ac.sanger.sccp.stan.service.operation.plan;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.plan.PlanRequest;
import uk.ac.sanger.sccp.stan.service.StringValidator;
import uk.ac.sanger.sccp.stan.service.StringValidator.CharacterType;
import uk.ac.sanger.sccp.stan.service.Validator;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author dr6
 */
@Component
public class PlanValidationFactory {
    private final LabwareRepo lwRepo;
    private final LabwareTypeRepo ltRepo;
    private final OperationTypeRepo opTypeRepo;
    private final Validator<String> prebarcodeValidator;

    @Autowired
    public PlanValidationFactory(LabwareRepo lwRepo, LabwareTypeRepo ltRepo, OperationTypeRepo opTypeRepo) {
        this.lwRepo = lwRepo;
        this.ltRepo = ltRepo;
        this.opTypeRepo = opTypeRepo;
        Set<CharacterType> charTypes = EnumSet.of(CharacterType.UPPER, CharacterType.LOWER, CharacterType.DIGIT, CharacterType.HYPHEN);
        Pattern pattern = Pattern.compile("[0-9]{7}[0-9A-Z]+-[0-9]+-[0-9]+-[0-9]+", Pattern.CASE_INSENSITIVE);
        this.prebarcodeValidator = new StringValidator("Barcode", 14, 32, charTypes, pattern);
    }

    public PlanValidation createPlanValidation(PlanRequest request) {
        return new PlanValidationImp(request, lwRepo, ltRepo, opTypeRepo, prebarcodeValidator);
    }
}
