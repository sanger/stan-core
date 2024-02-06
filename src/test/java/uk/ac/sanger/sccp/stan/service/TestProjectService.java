package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.sanger.sccp.stan.model.Project;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.repo.ProjectRepo;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
        service = spy(new ProjectService(mockRepo, mockUserRepo, simpleValidator(), mockEmailService));
    }

    @ParameterizedTest
    @MethodSource("addNewArgs")
    public void testAddNew(String string, String existingString, Exception expectedException, String expectedResultString) {
        genericTestAddNew(ProjectService::addNew,
                string, existingString, expectedException, expectedResultString);
    }

    @Test
    public void testAddNewByEndUser() {
        User creator = new User(100, "user1", User.Role.enduser);
        Project project = new Project(200, "Bananas");
        when(mockRepo.save(any())).thenReturn(project);
        doNothing().when(service).sendNewEntityEmail(any(), any());
        assertSame(project, service.addNew(creator, "Bananas"));
        verify(service).sendNewEntityEmail(creator, project);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testSendNewEntityEmail(boolean anyAdmin) {
        List<User> adminUsers = anyAdmin ? List.of(new User(1, "admin1", User.Role.admin),
                new User(2, "admin2", User.Role.admin)) : List.of();
        when(mockUserRepo.findAllByRole(User.Role.admin)).thenReturn(adminUsers);
        Project item = new Project(100, "Bananas");
        User creator = new User(1, "jeff", User.Role.enduser);

        service.sendNewEntityEmail(creator, item);

        if (!anyAdmin) {
            verifyNoInteractions(mockEmailService);
            return;
        }
        List<String> recipients = List.of("admin1", "admin2");
        verify(mockEmailService).tryEmail(recipients, "%service new Project",
                "User jeff has created a new Project on %service: Bananas");
    }

    @ParameterizedTest
    @MethodSource("setEnabledArgs")
    public void testSetEnabled(String string, boolean newValue, Boolean oldValue, Exception expectedException) {
        genericTestSetEnabled(ProjectService::setEnabled,
                string, newValue, oldValue, expectedException);
    }
}
