package uk.ac.sanger.sccp.stan;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.repo.UserRepo;
import uk.ac.sanger.sccp.stan.service.LDAPService;

import java.util.*;

import static java.util.Collections.singletonMap;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
/**
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
public class TestLogin {

    @MockBean
    private LDAPService mockLdapService;
    @MockBean
    private UserRepo mockUserRepo;
    @MockBean
    private AuthenticationComponent mockAuthComp;

    @Autowired
    private MockMvc mockMvc;

    private void postGraphql(String query, Object expectedResult) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/graphql")
                .content(query)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(request().asyncResult(expectedResult))
                .andReturn();
    }

    @Test
    public void testLogin() throws Exception {
        when(mockLdapService.verifyCredentials("dr6", "42")).thenReturn(true);
        when(mockUserRepo.findByUsername("dr6")).thenReturn(Optional.of(new User(10, "dr6")));
        String query = "{\"query\":\"mutation { login(username: \\\"dr6\\\", password: \\\"42\\\")  { user { username }}}\"}";
        Map<String, ?> expectedResult = Map.of("data", Map.of("login", Map.of("user", Map.of("username", "dr6"))));
        postGraphql(query, expectedResult);
        verify(mockLdapService).verifyCredentials("dr6", "42");
        verify(mockUserRepo).findByUsername("dr6");
        verify(mockAuthComp).setAuthentication(eq(new UsernamePasswordAuthenticationToken("dr6", "42", new ArrayList<>())), anyInt());
    }

    @Test
    public void testLogOut() throws Exception {
        String query = "{\"query\":\"mutation { logout }\"}";
        postGraphql(query, Map.of("data", Map.of("logout", "OK")));
        verify(mockAuthComp).setAuthentication(isNull(), anyInt());
    }

    @Test
    public void testGetUser() throws Exception {
        String query = "{\"query\":\"{user { username }}\"}";
        Map<String, ?> noUser = singletonMap("data", singletonMap("user", null));
        postGraphql(query, noUser);
        when(mockAuthComp.getAuthentication()).thenReturn(new UsernamePasswordAuthenticationToken("dr6", "42", new ArrayList<>()));
        Map<String, ?> foundUser = singletonMap("data", singletonMap("user", singletonMap("username", "dr6")));
        postGraphql(query, foundUser);
    }
}
