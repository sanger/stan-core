package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.model.OmeroProject;
import uk.ac.sanger.sccp.stan.repo.OmeroProjectRepo;

import static org.mockito.Mockito.mock;

/**
 * Tests {@link OmeroProjectAdminService}
 * @author dr6
 */
public class TestOmeroProjectAdminService extends AdminServiceTestUtils<OmeroProject, OmeroProjectRepo, OmeroProjectAdminService> {
    public TestOmeroProjectAdminService() {
        super("Omero project", OmeroProject::new, OmeroProjectRepo::findByName, "Name not supplied.");
    }

    @BeforeEach
    void setup() {
        mockRepo = mock(OmeroProjectRepo.class);
        service = new OmeroProjectAdminService(mockRepo, simpleValidator(), mockTransactor, mockNotifyService);
    }

    @ParameterizedTest
    @MethodSource("addNewArgs")
    public void testAddNew(String string, String existingString, Exception expectedException, String expectedResultString) {
        genericTestAddNew(OmeroProjectAdminService::addNew,
                string, existingString, expectedException, expectedResultString);
    }

    @ParameterizedTest
    @MethodSource("setEnabledArgs")
    public void testSetEnabled(String string, boolean newValue, Boolean oldValue, Exception expectedException) {
        genericTestSetEnabled(OmeroProjectAdminService::setEnabled,
                string, newValue, oldValue, expectedException);
    }
}
