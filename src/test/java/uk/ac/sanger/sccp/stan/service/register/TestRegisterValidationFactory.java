package uk.ac.sanger.sccp.stan.service.register;

import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.register.RegisterRequest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

/**
 * Tests {@link RegisterValidationFactory}
 * @author dr6
 */
public class TestRegisterValidationFactory {
    @Test
    public void testCreateRegisterValidation() {
        RegisterValidationFactory rgf = new RegisterValidationFactory(
                mock(DonorRepo.class), mock(HmdmcRepo.class), mock(TissueTypeRepo.class),
                mock(LabwareTypeRepo.class), mock(MouldSizeRepo.class), mock(MediumRepo.class),
                mock(FixativeRepo.class), mock(TissueRepo.class), mock(SpeciesRepo.class));
        assertNotNull(rgf.createRegisterValidation(new RegisterRequest()));
    }
}
