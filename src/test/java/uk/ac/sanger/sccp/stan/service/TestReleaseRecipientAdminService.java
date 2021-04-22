package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.model.ReleaseRecipient;
import uk.ac.sanger.sccp.stan.repo.ReleaseRecipientRepo;

import static org.mockito.Mockito.mock;

/**
 * Tests {@link ReleaseRecipientAdminService}
 * @author dr6
 */
public class TestReleaseRecipientAdminService extends AdminServiceTestUtils<ReleaseRecipient, ReleaseRecipientRepo, ReleaseRecipientAdminService> {
    public TestReleaseRecipientAdminService() {
        super("Release recipient", ReleaseRecipient::new, ReleaseRecipientRepo::findByUsername, "Username not supplied.");
    }

    @BeforeEach
    void setup() {
        mockRepo = mock(ReleaseRecipientRepo.class);
        service = new ReleaseRecipientAdminService(mockRepo, simpleValidator());
    }

    @ParameterizedTest
    @MethodSource("addNewArgs")
    public void testAddNew(String string, String existingString, Exception expectedException, String expectedResultString) {
        genericTestAddNew(ReleaseRecipientAdminService::addNew,
                string, existingString, expectedException, expectedResultString);
    }

    @ParameterizedTest
    @MethodSource("setEnabledArgs")
    public void testSetEnabled(String string, boolean newValue, Boolean oldValue, Exception expectedException) {
        genericTestSetEnabled(ReleaseRecipientAdminService::setEnabled,
                string, newValue, oldValue, expectedException);
    }
}
