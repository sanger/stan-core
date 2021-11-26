package uk.ac.sanger.sccp.stan.integrationtest;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGetList;

/**
 * Tests admin mutations of users
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})public class TestUserMutations {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;

    @Autowired
    private UserRepo userRepo;

    @Test
    @Transactional
    public void testAddUserNonAdmin() throws Exception {
        tester.setUser(entityCreator.createUser("normo", User.Role.normal));
        String mutation = tester.readGraphQL("adduser.graphql");
        Object result = tester.post(mutation);
        String errorMessage = chainGet(result, "errors", 0, "message");
        assertThat(errorMessage).contains("Requires role: admin");
        assertThat(userRepo.findByUsername("ford")).isEmpty();
    }

    @Test
    @Transactional
    public void testAddUserAndSetRole() throws Exception {
        tester.setUser(entityCreator.createUser("admo", User.Role.admin));
        String mutation = tester.readGraphQL("adduser.graphql");
        Object result = tester.post(mutation);
        Map<String, String> userMap = Map.of("username", "ford", "role", "normal");
        assertEquals(userMap, chainGet(result, "data", "addUser"));
        final String userQuery = "query { users { username, role }}";
        result = tester.post(userQuery);
        assertThat(chainGetList(result, "data", "users")).contains(userMap);

        mutation = tester.readGraphQL("setuserrole.graphql");
        result = tester.post(mutation);
        userMap = Map.of("username", "ford", "role", "disabled");
        assertEquals(userMap, chainGet(result, "data", "setUserRole"));
        result = tester.post(userQuery);
        assertThat(chainGetList(result, "data", "users")).noneMatch(map -> "ford".equalsIgnoreCase((String) ((Map<?,?>) map).get("username")));

        mutation = mutation.replace("disabled", "normal");
        result = tester.post(mutation);
        userMap = Map.of("username", "ford", "role", "normal");
        assertEquals(userMap, chainGet(result, "data", "setUserRole"));
        result = tester.post(userQuery);
        assertThat(chainGetList(result, "data", "users")).contains(userMap);
    }
}
