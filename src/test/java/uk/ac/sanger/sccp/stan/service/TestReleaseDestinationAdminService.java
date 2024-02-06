package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.model.ReleaseDestination;
import uk.ac.sanger.sccp.stan.repo.ReleaseDestinationRepo;

import static org.mockito.Mockito.mock;

/**
 * Tests {@link ReleaseDestinationAdminService}
 * @author dr6
 */
public class TestReleaseDestinationAdminService extends AdminServiceTestUtils<ReleaseDestination, ReleaseDestinationRepo, ReleaseDestinationAdminService> {
    public TestReleaseDestinationAdminService() {
        super("Release destination", ReleaseDestination::new, ReleaseDestinationRepo::findByName, "Name not supplied.");
    }

    @BeforeEach
    void setup() {
        mockRepo = mock(ReleaseDestinationRepo.class);
        service = new ReleaseDestinationAdminService(mockRepo, mockUserRepo, simpleValidator(), mockEmailService);
    }

    @ParameterizedTest
    @MethodSource("addNewArgs")
    public void testAddNew(String string, String existingString, Exception expectedException, String expectedResultString) {
        genericTestAddNew(ReleaseDestinationAdminService::addNew,
                string, existingString, expectedException, expectedResultString);
    }

    @ParameterizedTest
    @MethodSource("setEnabledArgs")
    public void testSetEnabled(String string, boolean newValue, Boolean oldValue, Exception expectedException) {
        genericTestSetEnabled(ReleaseDestinationAdminService::setEnabled,
                string, newValue, oldValue, expectedException);
    }
}
