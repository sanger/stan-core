package uk.ac.sanger.sccp.stan.service.register;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.register.RegisterRequest;
import uk.ac.sanger.sccp.stan.request.register.SectionRegisterRequest;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.sanitiser.Sanitiser;
import uk.ac.sanger.sccp.stan.service.work.WorkService;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

/**
 * Tests {@link RegisterValidationFactory}
 * @author dr6
 */
public class TestRegisterValidationFactory {
    RegisterValidationFactory registerValidationFactory;
    @SuppressWarnings("unchecked")
    @BeforeEach
    void setup() {
        Validator<String> mockStringValidator = mock(Validator.class);
        Sanitiser<String> mockSanitiser = mock(Sanitiser.class);
        registerValidationFactory = new RegisterValidationFactory(
                mock(DonorRepo.class), mock(HmdmcRepo.class), mock(TissueTypeRepo.class),
                mock(LabwareTypeRepo.class), mock(MediumRepo.class),
                mock(FixativeRepo.class), mock(TissueRepo.class), mock(SpeciesRepo.class), mock(LabwareRepo.class),
                mock(BioStateRepo.class), mockStringValidator, mockStringValidator, mockStringValidator,
                mockStringValidator, mockStringValidator, mockSanitiser, mockStringValidator,
                mock(TissueFieldChecker.class), mock(SlotRegionService.class), mock(BioRiskService.class),
                mock(WorkService.class));
    }

    @Test
    public void testCreateRegisterValidation() {
        assertNotNull(registerValidationFactory.createRegisterValidation(new RegisterRequest()));
    }

    @Test
    public void testCreateSectionRegisterValidation() {
        assertNotNull(registerValidationFactory.createSectionRegisterValidation(new SectionRegisterRequest()));
    }
}
