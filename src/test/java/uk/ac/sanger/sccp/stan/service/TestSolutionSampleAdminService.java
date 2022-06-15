package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.model.SolutionSample;
import uk.ac.sanger.sccp.stan.repo.SolutionSampleRepo;

import static org.mockito.Mockito.mock;

/**
 * Tests {@link SolutionSampleAdminService}
 * @author dr6
 */
public class TestSolutionSampleAdminService extends AdminServiceTestUtils<SolutionSample, SolutionSampleRepo, SolutionSampleAdminService> {
    public TestSolutionSampleAdminService() {
        super("Solution sample", SolutionSample::new,
                SolutionSampleRepo::findByName, "Name not supplied.");
    }

    @BeforeEach
    void setup() {
        mockRepo = mock(SolutionSampleRepo.class);
        service = new SolutionSampleAdminService(mockRepo, simpleValidator());
    }

    @ParameterizedTest
    @MethodSource("addNewArgs")
    public void testAddNew(String string, String existingString, Exception expectedException, String expectedResultString) {
        genericTestAddNew(SolutionSampleAdminService::addNew,
                string, existingString, expectedException, expectedResultString);
    }

    @ParameterizedTest
    @MethodSource("setEnabledArgs")
    public void testSetEnabled(String string, boolean newValue, Boolean oldValue, Exception expectedException) {
        genericTestSetEnabled(SolutionSampleAdminService::setEnabled, string, newValue, oldValue, expectedException);
    }
}
