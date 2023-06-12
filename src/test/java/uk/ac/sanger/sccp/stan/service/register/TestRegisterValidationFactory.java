package uk.ac.sanger.sccp.stan.service.register;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.register.RegisterRequest;
import uk.ac.sanger.sccp.stan.request.register.SectionRegisterRequest;
import uk.ac.sanger.sccp.stan.service.SlotRegionService;
import uk.ac.sanger.sccp.stan.service.Validator;
import uk.ac.sanger.sccp.stan.service.work.WorkService;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

/**
 * Tests {@link RegisterValidationFactory}
 * @author dr6
 */
public class TestRegisterValidationFactory {
    RegisterValidationFactory registerValidationFactory;
    @BeforeEach
    void setup() {
        //noinspection unchecked
        Validator<String> mockStringValidator = mock(Validator.class);
        registerValidationFactory = new RegisterValidationFactory(
                mock(DonorRepo.class), mock(HmdmcRepo.class), mock(TissueTypeRepo.class),
                mock(LabwareTypeRepo.class), mock(MediumRepo.class),
                mock(FixativeRepo.class), mock(TissueRepo.class), mock(SpeciesRepo.class), mock(LabwareRepo.class),
                mock(BioStateRepo.class), mockStringValidator, mockStringValidator, mockStringValidator,
                mockStringValidator, mockStringValidator,
                mock(TissueFieldChecker.class), mock(SlotRegionService.class), mock(WorkService.class));
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
