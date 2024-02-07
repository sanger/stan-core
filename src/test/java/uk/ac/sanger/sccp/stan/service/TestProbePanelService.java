package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.model.ProbePanel;
import uk.ac.sanger.sccp.stan.repo.ProbePanelRepo;

import static org.mockito.Mockito.mock;

/**
 * Tests {@link ProbePanelService}
 * @author dr6
 */
public class TestProbePanelService extends AdminServiceTestUtils<ProbePanel, ProbePanelRepo, ProbePanelService> {
    public TestProbePanelService() {
        super("ProbePanel", ProbePanel::new, ProbePanelRepo::findByName, "Name not supplied.");
    }

    @BeforeEach
    void setup() {
        mockRepo = mock(ProbePanelRepo.class);
        service = new ProbePanelService(mockRepo, simpleValidator(), mockTransactor, mockNotifyService);
    }

    @ParameterizedTest
    @MethodSource("addNewArgs")
    public void testAddNew(String string, String existingString, Exception expectedException, String expectedResultString) {
        genericTestAddNew(ProbePanelService::addNew, string, existingString, expectedException, expectedResultString);
    }

    @ParameterizedTest
    @MethodSource("setEnabledArgs")
    public void testSetEnabled(String string, boolean newValue, Boolean oldValue, Exception expectedException) {
        genericTestSetEnabled(ProbePanelService::setEnabled, string, newValue, oldValue, expectedException);
    }
}
