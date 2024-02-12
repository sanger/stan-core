package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import uk.ac.sanger.sccp.stan.AuthenticationComponent;
import uk.ac.sanger.sccp.stan.config.SessionConfig;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.repo.UserRepo;
import uk.ac.sanger.sccp.stan.request.LoginResult;

import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

/** Test {@link AuthServiceImp} */
class TestAuthService {
    @Mock
    private SessionConfig mockSessionConfig;
    @Mock
    private UserRepo mockUserRepo;
    @Mock
    private AuthenticationComponent mockAuthComp;
    @Mock
    private LDAPService mockLdapService;
    @Mock
    private EmailService mockEmailService;
    @Mock
    private UserAdminService mockUserAdminService;

    @InjectMocks
    private AuthServiceImp service;

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
    @ValueSource(booleans={true,false})
    void testLoggedInUsername(boolean loggedIn) {
        String username;
        if (loggedIn) {
            username = "user1";
            User user = new User(100, username, User.Role.normal);
            Authentication auth = mock(Authentication.class);
            when(auth.getPrincipal()).thenReturn(user);
            when(mockAuthComp.getAuthentication()).thenReturn(auth);
        } else {
            username = null;
            when(mockAuthComp.getAuthentication()).thenReturn(null);
        }
        assertEquals(username, service.loggedInUsername());
    }

    @ParameterizedTest
    @CsvSource({"normal,true",
            ",true",
            "disabled,true",
            "normal,false",
    })
    void testLogin(User.Role role, boolean valid) {
        String username = "un";
        String password = "pw";
        final int maxInactiveMinutes = 10;
        User foundUser = role==null ? null : new User(100, username, role);
        when(mockLdapService.verifyCredentials(username, password)).thenReturn(valid);
        when(mockUserRepo.findByUsername(username)).thenReturn(Optional.ofNullable(foundUser));
        when(mockSessionConfig.getMaxInactiveMinutes()).thenReturn(maxInactiveMinutes);
        LoginResult result = service.logIn(username, password);
        String expectedMessage;
        User expectedUser = null;
        if (role==null) {
            expectedMessage = "Username not in database.";
        } else if (role== User.Role.disabled) {
            expectedMessage = "Username is disabled.";
        } else if (!valid) {
            expectedMessage = "Login failed.";
        } else {
            expectedUser = foundUser;
            expectedMessage = "OK";
        }
        assertEquals(expectedMessage, result.getMessage());
        assertSame(expectedUser, result.getUser());
        if (result.getUser()!=null) {
            Authentication authentication = new UsernamePasswordAuthenticationToken(foundUser, password, new ArrayList<>());
            verify(mockAuthComp).setAuthentication(authentication, maxInactiveMinutes);
        } else {
            verifyNoInteractions(mockAuthComp);
        }
    }

    @Test
    void testLogOut() {
        service.logOut();
        verify(mockAuthComp).setAuthentication(null, 0);
    }

    @ParameterizedTest
    @CsvSource({
            ", true",
            "normal, true",
            "disabled, true",
            ", false",
    })
    void testSelfRegister(User.Role existingRole, boolean valid) {
        final int maxInactiveMinutes = 10;
        final String sanUsername = "user1";
        final String password = "pw";
        final String inputUsername = " USER1";
        final User.Role suppliedRole = User.Role.enduser;
        when(mockSessionConfig.getMaxInactiveMinutes()).thenReturn(maxInactiveMinutes);
        User existingUser = (existingRole==null ? null : new User(100, sanUsername, existingRole));
        when(mockUserAdminService.validateUsername(inputUsername)).thenReturn(sanUsername);
        when(mockLdapService.verifyCredentials(sanUsername, password)).thenReturn(valid);
        when(mockUserRepo.findByUsername(sanUsername)).thenReturn(Optional.ofNullable(existingUser));
        User newUser;
        if (existingRole==null && valid) {
            newUser = new User(200, sanUsername, suppliedRole);
            when(mockUserAdminService.addUser(sanUsername, suppliedRole)).thenReturn(newUser);
        } else {
            newUser = null;
        }
        doNothing().when(service).sendNewUserEmail(any());
        User expectedUser;
        String expectedMessage;
        LoginResult result = service.selfRegister(inputUsername, password, suppliedRole);
        if (newUser!=null) {
            expectedUser = newUser;
            expectedMessage = "OK";
        } else if (valid && existingRole != User.Role.disabled) {
            expectedUser = existingUser;
            expectedMessage = "OK";
        } else {
            expectedUser = null;
            expectedMessage = valid ? "Username is disabled." : "Authentication failed.";
        }

        assertSame(expectedUser, result.getUser());
        assertEquals(expectedMessage, result.getMessage());

        if (expectedUser!=null) {
            Authentication authentication = new UsernamePasswordAuthenticationToken(expectedUser, password, new ArrayList<>());
            verify(mockAuthComp).setAuthentication(authentication, maxInactiveMinutes);
        } else {
            verifyNoInteractions(mockAuthComp);
        }
        if (newUser!=null) {
            verify(service).sendNewUserEmail(newUser);
        } else {
            verify(service, never()).sendNewUserEmail(any());
        }
    }


    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void testSendNewUserEmail(boolean anyAdmins) {
        User newUser = new User(100, "user1", User.Role.enduser);
        List<User> adminUsers;
        if (anyAdmins) {
            adminUsers = IntStream.rangeClosed(1,2)
                    .mapToObj(i -> new User(100+i, "admin"+i, User.Role.admin))
                    .toList();
        } else {
            adminUsers = List.of();
        }
        when(mockUserRepo.findAllByRole(User.Role.admin)).thenReturn(adminUsers);
        when(mockEmailService.tryEmail(any(), any(), any())).thenReturn(true);

        service.sendNewUserEmail(newUser);

        if (!anyAdmins) {
            verifyNoInteractions(mockEmailService);
        } else {
            List<String> adminUsernames = adminUsers.stream().map(User::getUsername).toList();
            verify(mockEmailService).tryEmail(adminUsernames, "New user created on %service",
                    "User user1 has registered themself as enduser on %service.");
        }
    }
}