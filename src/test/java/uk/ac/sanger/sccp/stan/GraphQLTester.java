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

    public <T> T post(String query, Object variables) throws Exception {
        JSONObject jo = new JSONObject();
        jo.put("query", query);
        if (variables!=null) {
            jo.put("variables", variables);
        }
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/graphql")
                .content(jo.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        //noinspection unchecked
        return (T) result.getAsyncResult();
    }

    public <T> T post(String query) throws Exception {
        return post(query, null);
    }

    public MockMvc getMockMvc() {
        return this.mockMvc;
    }

    public void setUser(User user) {
        when(mockAuthComp.getAuthentication()).thenReturn(new UsernamePasswordAuthenticationToken(user, "42"));
    }

    @SuppressWarnings("UnstableApiUsage")
    public String readResource(String path) throws IOException {
        URL url = Resources.getResource(path);
        return Resources.toString(url, Charsets.UTF_8);
    }
}
