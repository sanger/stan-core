package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.model.Hmdmc;
import uk.ac.sanger.sccp.stan.repo.HmdmcRepo;

import static org.mockito.Mockito.mock;

/**
 * Tests {@link HmdmcAdminService}
 * @author dr6
 */
public class TestHmdmcAdminService extends AdminServiceTestUtils<Hmdmc, HmdmcRepo, HmdmcAdminService> {
    public TestHmdmcAdminService() {
        super("HuMFre", Hmdmc::new, HmdmcRepo::findByHmdmc, "HuMFre not supplied.");
    }

    @BeforeEach
    void setup() {
        mockRepo = mock(HmdmcRepo.class);
        service = new HmdmcAdminService(mockRepo, mockUserRepo, simpleValidator(), mockEmailService);
    }

    @ParameterizedTest
    @MethodSource("addNewArgs")
    public void testAddNew(String string, String existingString, Exception expectedException, String expectedResultString) {
        genericTestAddNew(HmdmcAdminService::addNew,
                string, existingString, expectedException, expectedResultString);
    }

    @ParameterizedTest
    @MethodSource("setEnabledArgs")
    public void testSetEnabled(String string, boolean newValue, Boolean oldValue, Exception expectedException) {
        genericTestSetEnabled(HmdmcAdminService::setEnabled,
                string, newValue, oldValue, expectedException);
    }
}
