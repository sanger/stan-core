package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.model.Project;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.repo.ProjectRepo;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.mockTransactor;

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
        service = spy(new ProjectService(mockRepo, simpleValidator(), mockTransactor, mockNotifyService));
    }

    @ParameterizedTest
    @MethodSource("addNewArgs")
    public void testAddNew(String string, String existingString, Exception expectedException, String expectedResultString) {
        genericTestAddNew(ProjectService::addNew,
                string, existingString, expectedException, expectedResultString);
    }

    @Test
    public void testAddNewByEndUser() {
        mockTransactor(mockTransactor);
        User creator = new User(100, "user1", User.Role.enduser);
        Project project = new Project(200, "Bananas");
        when(mockRepo.save(any())).thenReturn(project);
        doNothing().when(service).sendNewEntityEmail(any(), any());
        assertSame(project, service.addNew(creator, "Bananas"));
        verify(service).sendNewEntityEmail(creator, project);
        verify(mockTransactor).transact(eq("Add Project"), any());
    }

    @Test
    public void testSendNewEntityEmail() {
        Project item = new Project(100, "Bananas");
        User creator = new User(1, "jeff", User.Role.enduser);

        service.sendNewEntityEmail(creator, item);
        verify(mockNotifyService).issue("project", "%service new Project",
                "User jeff has created a new Project on %service: Bananas");
    }

    @ParameterizedTest
    @MethodSource("setEnabledArgs")
    public void testSetEnabled(String string, boolean newValue, Boolean oldValue, Exception expectedException) {
        genericTestSetEnabled(ProjectService::setEnabled,
                string, newValue, oldValue, expectedException);
    }
}
