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
import uk.ac.sanger.sccp.stan.service.LDAPService;

import java.util.*;

import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
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

    @Autowired
    private GraphQLTester tester;

    @Test
    public void testLogin() throws Exception {
        when(mockLdapService.verifyCredentials("dr6", "42")).thenReturn(true);
        when(mockUserRepo.findByUsername("dr6")).thenReturn(Optional.of(new User(10, "dr6")));
        String mutation = "mutation { login(username: \"dr6\", password: \"42\")  { user { username } } }";
        Object result = tester.post(mutation);
        Map<String, ?> expectedResult = Map.of("data", Map.of("login", Map.of("user", Map.of("username", "dr6"))));
        assertEquals(expectedResult, result);
        verify(mockLdapService).verifyCredentials("dr6", "42");
        verify(mockUserRepo).findByUsername("dr6");
        verify(tester.mockAuthComp).setAuthentication(eq(new UsernamePasswordAuthenticationToken("dr6", "42", new ArrayList<>())), anyInt());
    }

    @Test
    public void testLogOut() throws Exception {
        Map<String, Map<String, String>> result = tester.post("mutation { logout }");
        assertEquals(Map.of("data", Map.of("logout", "OK")), result);
        verify(tester.mockAuthComp).setAuthentication(isNull(), anyInt());
    }

    @Test
    public void testGetUser() throws Exception {
        final String query = "{ user { username } }";
        Map<String, Map<String, Map<String, String>>> noUserResult = tester.post(query);
        assertEquals(Map.of("data", singletonMap("user", null)), noUserResult);

        tester.setUser("dr6");
        Map<String, Map<String, Map<String, String>>> userResult = tester.post(query);
        assertEquals(Map.of("data", Map.of("user", Map.of("username", "dr6"))), userResult);
    }
}
