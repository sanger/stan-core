package uk.ac.sanger.sccp.stan;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.service.label.print.PrintClientFactory;

import java.io.IOException;
import java.net.URL;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tool for helping test graphql api
 * @author dr6
 */
@TestComponent
public class GraphQLTester {

    @MockBean
    AuthenticationComponent mockAuthComp;
    @MockBean
    PrintClientFactory mockPrintClientFactory;
    @Autowired
    private MockMvc mockMvc;

    public <T> T post(String query) throws Exception {
        JSONObject jo = new JSONObject();
        jo.put("query", query);
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/graphql")
                .content(jo.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        //noinspection unchecked
        return (T) result.getAsyncResult();
    }

    public void setUser(String username) {
        when(mockAuthComp.getAuthentication()).thenReturn(new UsernamePasswordAuthenticationToken(username, "42"));
    }

    public void setUser(User user) {
        setUser(user.getUsername());
    }

    @SuppressWarnings("UnstableApiUsage")
    public String readResource(String path) throws IOException {
        URL url = Resources.getResource(path);
        return Resources.toString(url, Charsets.UTF_8);
    }
}
