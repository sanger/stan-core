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

import javax.transaction.Transactional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;

/**
 * Tests the findLatestOp query
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestFindLatestOpQuery {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;

    @Transactional
    @Test
    public void testFindLatestOp() throws Exception {
        String mutation = tester.readGraphQL("register.graphql").replace("\"SGP1\"", "");
        User user = entityCreator.createUser("user1");
        tester.setUser(user);
        Object mutationResult = tester.post(mutation);
        String barcode = chainGet(mutationResult, "data", "register", "labware", 0, "barcode");

        String query = tester.readGraphQL("findlatestop.graphql").replace("STAN-A1", barcode);
        Object result = tester.post(query);
        assertNotNull(chainGet(result, "data", "findLatestOp", "id"));

        Object noResult = tester.post(query.replace("Register", "Section"));
        assertNull(chainGet(noResult, "data", "findLatestOp"));
    }
}
