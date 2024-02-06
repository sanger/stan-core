package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.model.Solution;
import uk.ac.sanger.sccp.stan.repo.SolutionRepo;

import static org.mockito.Mockito.mock;

/**
 * Tests {@link SolutionAdminService}
 * @author dr6
 */
public class TestSolutionAdminService extends AdminServiceTestUtils<Solution, SolutionRepo, SolutionAdminService> {
    public TestSolutionAdminService() {
        super("Solution", Solution::new,
                SolutionRepo::findByName, "Name not supplied.");
    }

    @BeforeEach
    void setup() {
        mockRepo = mock(SolutionRepo.class);
        service = new SolutionAdminService(mockRepo, mockUserRepo, simpleValidator(), mockEmailService);
    }

    @ParameterizedTest
    @MethodSource("addNewArgs")
    public void testAddNew(String string, String existingString, Exception expectedException, String expectedResultString) {
        genericTestAddNew(SolutionAdminService::addNew,
                string, existingString, expectedException, expectedResultString);
    }

    @ParameterizedTest
    @MethodSource("setEnabledArgs")
    public void testSetEnabled(String string, boolean newValue, Boolean oldValue, Exception expectedException) {
        genericTestSetEnabled(SolutionAdminService::setEnabled, string, newValue, oldValue, expectedException);
    }
}
