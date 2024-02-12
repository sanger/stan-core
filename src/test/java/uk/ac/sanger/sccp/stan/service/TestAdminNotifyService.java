package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import uk.ac.sanger.sccp.stan.config.MailConfig;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.repo.UserRepo;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

/** Tests {@link AdminNotifyServiceImp} */
class TestAdminNotifyService {
    @Mock
    MailConfig mockMailConfig;
    @Mock
    UserRepo mockUserRepo;
    @Mock
    EmailService mockEmailService;

    @InjectMocks
    AdminNotifyServiceImp service;

    private AutoCloseable mocking;

    @BeforeEach
    void setup() {
        mocking = MockitoAnnotations.openMocks(this);
        service = spy(service);
    }

    @AfterEach
    void cleanup() throws Exception {
        mocking.close();
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void isNotificationEnabled(boolean enabled) {
        final String name = "Banana";
        when(mockMailConfig.isAdminNotificationEnabled(name)).thenReturn(enabled);
        assertEquals(enabled, service.isNotificationEnabled(name));
    }

    @Test
    void substitute() {
        when(mockMailConfig.getServiceDescription()).thenReturn("Stan test");
        assertEquals("Alpha Stan test beta", service.substitute("Alpha %service beta"));
        assertEquals("", service.substitute(""));
        assertNull(service.substitute(null));
    }

    @ParameterizedTest
    @ValueSource(ints={0,1,2,3})
    void issue(int phase) {
        // 0 disabled
        // 1 no admin
        // 2 send fails
        // 3 success
        final String name = "alpha";
        doReturn(phase!=0).when(service).isNotificationEnabled(name);
        List<User> admins = (phase==1 ? List.of() : List.of(new User(1, "admin1", User.Role.admin),
                new User(2, "admin2", User.Role.admin)));
        when(mockUserRepo.findAllByRole(User.Role.admin)).thenReturn(admins);
        doReturn(phase!=2).when(service).sendNotification(any(), any(), any());

        assertEquals(phase==3, service.issue(name, "%service heading", "Body %service"));

        if (phase >= 2) {
            verify(service).sendNotification(List.of("admin1", "admin2"), "%service heading", "Body %service");
        } else {
            verify(service, never()).sendNotification(any(), any(), any());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void sendNotification(boolean result) {
        when(mockMailConfig.getServiceDescription()).thenReturn("Stan test");
        when(mockEmailService.tryEmail(any(), any(), any())).thenReturn(result);
        List<String> recipients = List.of("admin1", "admin2");
        assertEquals(result, service.sendNotification(recipients, "%service heading", "Body %service"));
        verify(mockEmailService).tryEmail(recipients, "Stan test heading", "Body Stan test");
    }
}