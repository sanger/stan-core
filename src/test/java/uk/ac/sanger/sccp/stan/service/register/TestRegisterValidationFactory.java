package uk.ac.sanger.sccp.stan.service.register;

import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import uk.ac.sanger.sccp.stan.request.register.BlockRegisterRequest;
import uk.ac.sanger.sccp.stan.request.register.SectionRegisterRequest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests {@link RegisterValidationFactory}
 * @author dr6
 */
public class TestRegisterValidationFactory {
    @InjectMocks
    RegisterValidationFactory registerValidationFactory;

    private AutoCloseable mocking;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
    }

    @Test
    public void testCreateBlockRegisterValidation() {
        assertNotNull(registerValidationFactory.createBlockRegisterValidation(new BlockRegisterRequest()));
    }

    @Test
    public void testCreateSectionRegisterValidation() {
        assertNotNull(registerValidationFactory.createSectionRegisterValidation(new SectionRegisterRequest()));
    }
}
