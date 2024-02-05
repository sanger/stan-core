package uk.ac.sanger.sccp.stan;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.repo.UserRepo;
import uk.ac.sanger.sccp.stan.service.EmailService;
import uk.ac.sanger.sccp.stan.service.LDAPService;

import java.util.*;
import java.util.stream.IntStream;

import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;

/**
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestLogin {

    @MockBean
    private LDAPService mockLdapService;
    @MockBean
    private UserRepo mockUserRepo;
    @MockBean
    private EmailService mockEmailService;

    @Autowired
    private GraphQLTester tester;

    @Test
    public void testLogin() throws Exception {
        when(mockLdapService.verifyCredentials("dr6", "42")).thenReturn(true);
        final User user = new User(10, "dr6", User.Role.admin);
        when(mockUserRepo.findByUsername("dr6")).thenReturn(Optional.of(user));
        String mutation = "mutation { login(username: \"dr6\", password: \"42\")  { user { username role } } }";
        Object result = tester.post(mutation);
        Map<String, ?> expectedResult = Map.of("data", Map.of("login", Map.of("user", Map.of("username", "dr6", "role", "admin"))));
        assertEquals(expectedResult, result);
        verify(mockLdapService).verifyCredentials("dr6", "42");
        verify(mockUserRepo).findByUsername("dr6");
        verify(tester.mockAuthComp).setAuthentication(eq(new UsernamePasswordAuthenticationToken(user, "42", new ArrayList<>())), anyInt());
    }

    @Test
    public void testLogOut() throws Exception {
        Map<String, Map<String, String>> result = tester.post("mutation { logout }");
        assertEquals(Map.of("data", Map.of("logout", "OK")), result);
        verify(tester.mockAuthComp).setAuthentication(isNull(), anyInt());
    }

    @Test
    public void testGetUser() throws Exception {
        final String query = "{ user { username role } }";
        Map<String, Map<String, Map<String, String>>> noUserResult = tester.post(query);
        assertEquals(Map.of("data", singletonMap("user", null)), noUserResult);

        tester.setUser(new User("dr6", User.Role.normal));
        Map<String, Map<String, Map<String, String>>> userResult = tester.post(query);
        assertEquals(Map.of("data", Map.of("user", Map.of("username", "dr6", "role", "normal"))), userResult);
    }

    @Test
    public void testSelfRegister() throws Exception {
        final String mutation = "mutation { registerAsEndUser(username: \"user1\", password: \"42\") " +
                "{ user { username role } } }";
        User newUser = new User(100, "user1", User.Role.enduser);
        List<User> adminUsers = IntStream.rangeClosed(1,2)
                .mapToObj(i -> new User(i, "admin"+i, User.Role.admin))
                .toList();
        List<String> recipients = adminUsers.stream().map(User::getUsername).toList();
        when(mockUserRepo.findAllByRole(User.Role.admin)).thenReturn(adminUsers);
        when(mockEmailService.tryEmail(any(), any(), any())).thenReturn(true);
        when(mockUserRepo.findByUsername(any())).thenReturn(Optional.empty());
        when(mockUserRepo.save(any())).thenReturn(newUser);
        when(mockLdapService.verifyCredentials("user1", "42")).thenReturn(true);

        Object response = tester.post(mutation);
        Map<String, String> userData = chainGet(response, "data", "registerAsEndUser", "user");
        assertEquals("user1", userData.get("username"));
        assertEquals("enduser", userData.get("role"));
        verify(mockUserRepo, atLeastOnce()).findByUsername(eq("user1"));
        verify(mockUserRepo).save(new User("user1", User.Role.enduser));
        verify(mockLdapService).verifyCredentials("user1", "42");
        verify(mockEmailService).tryEmail(recipients, "New user created on %service",
                "User user1 has registered themself as enduser on %service.");

        verify(tester.mockAuthComp).setAuthentication(eq(new UsernamePasswordAuthenticationToken(newUser, "42", new ArrayList<>())), anyInt());

    }
}
