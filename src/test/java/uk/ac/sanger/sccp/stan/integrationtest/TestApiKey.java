package uk.ac.sanger.sccp.stan.integrationtest;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.GraphQLTester;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.repo.UserRepo;

import javax.transaction.Transactional;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;

/**
 * Tests using the API key
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestApiKey {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private UserRepo userRepo;

    @Test
    @Transactional
    public void testApiKey() throws Exception {
        userRepo.save(new User("patch", User.Role.admin));
        JSONObject variables = new JSONObject();
        variables.put("STAN-APIKEY", "devapikey");
        Object data = tester.post("query { user { username, role }}", variables);
        Map<String, ?> userData = chainGet(data, "data", "user");
        assertEquals("patch", userData.get("username"));
        assertEquals("admin", userData.get("role"));

        String mutation = tester.readGraphQL("addnewcomment.graphql");
        data = tester.post(mutation, variables);
        assertEquals("Fell in the bin.", chainGet(data, "data", "addComment", "text"));
    }
}
