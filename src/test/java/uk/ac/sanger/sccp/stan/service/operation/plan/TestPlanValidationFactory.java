package uk.ac.sanger.sccp.stan.service.operation.plan;

import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.plan.PlanRequest;
import uk.ac.sanger.sccp.stan.service.Validator;
import uk.ac.sanger.sccp.stan.service.sanitiser.Sanitiser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static uk.ac.sanger.sccp.stan.Matchers.genericMock;

/**
 * Tests for {@link PlanValidationFactory}
 * @author dr6
 */
public class TestPlanValidationFactory {
    @Test
    public void testCreatePlanValidation() {
        LabwareRepo lwRepo = mock(LabwareRepo.class);
        LabwareTypeRepo ltRepo = mock(LabwareTypeRepo.class);
        OperationTypeRepo opTypeRepo = mock(OperationTypeRepo.class);
        Validator<String> mockVisiumValidator = genericMock(Validator.class);
        Validator<String> mockLotValidator = genericMock(Validator.class);
        Validator<String> mockXeniumValidator = genericMock(Validator.class);
        Sanitiser<String> mockThicknessSanitiser = genericMock(Sanitiser.class);
        PlanValidationFactory factory = new PlanValidationFactory(lwRepo, ltRepo, opTypeRepo,
                mockVisiumValidator, mockXeniumValidator, mockLotValidator, mockThicknessSanitiser);
        PlanRequest request = new PlanRequest();
        PlanValidation validation = factory.createPlanValidation(request);
        assertThat(validation).isInstanceOf(PlanValidationImp.class);
        PlanValidationImp validationImp = (PlanValidationImp) validation;
        assertSame(validationImp.labwareRepo, lwRepo);
        assertSame(validationImp.ltRepo, ltRepo);
        assertSame(validationImp.opTypeRepo, opTypeRepo);
        assertSame(validationImp.request, request);
        assertNotNull(validationImp.problems);
        assertSame(validationImp.visiumBarcodeValidator, mockVisiumValidator);
        assertSame(validationImp.xeniumBarcodeValidator, mockXeniumValidator);
        assertSame(validationImp.lotValidator, mockLotValidator);
        assertSame(validationImp.thicknessSanitiser, mockThicknessSanitiser);
    }
}
