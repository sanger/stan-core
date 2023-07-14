package uk.ac.sanger.sccp.stan.service.operation.plan;

import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.plan.PlanRequest;
import uk.ac.sanger.sccp.stan.service.Validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link PlanValidationFactory}
 * @author dr6
 */
public class TestPlanValidationFactory {
    @SuppressWarnings("unchecked")
    @Test
    public void testCreatePlanValidation() {
        LabwareRepo lwRepo = mock(LabwareRepo.class);
        LabwareTypeRepo ltRepo = mock(LabwareTypeRepo.class);
        OperationTypeRepo opTypeRepo = mock(OperationTypeRepo.class);
        Validator<String> mockVisiumValidator = mock(Validator.class);
        Validator<String> mockLotValidator = mock(Validator.class);
        Validator<String> mockXeniumValidator = mock(Validator.class);
        PlanValidationFactory factory = new PlanValidationFactory(lwRepo, ltRepo, opTypeRepo,
                mockVisiumValidator, mockXeniumValidator, mockLotValidator);
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
    }
}
