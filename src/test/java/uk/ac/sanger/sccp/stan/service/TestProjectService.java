package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.model.Project;
import uk.ac.sanger.sccp.stan.repo.ProjectRepo;

import static org.mockito.Mockito.mock;

/**
 * Tests {@link ProjectService}
 * @author dr6
 */
public class TestProjectService extends AdminServiceTestUtils<Project, ProjectRepo, ProjectService> {
    public TestProjectService() {
        super("Project", Project::new,
                ProjectRepo::findByName, "Name not supplied.");
    }

    @BeforeEach
    void setup() {
        mockRepo = mock(ProjectRepo.class);
        service = new ProjectService(mockRepo, simpleValidator());
    }

    @ParameterizedTest
    @MethodSource("addNewArgs")
    public void testAddNew(String string, String existingString, Exception expectedException, String expectedResultString) {
        genericTestAddNew(ProjectService::addNew,
                string, existingString, expectedException, expectedResultString);
    }

    @ParameterizedTest
    @MethodSource("setEnabledArgs")
    public void testSetEnabled(String string, boolean newValue, Boolean oldValue, Exception expectedException) {
        genericTestSetEnabled(ProjectService::setEnabled,
                string, newValue, oldValue, expectedException);
    }
}
