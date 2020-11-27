package uk.ac.sanger.sccp.stan;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.model.LifeStage;
import uk.ac.sanger.sccp.stan.model.Sample;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Non-exhaustive integration tests.
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class IntegrationTests {

    @Autowired
    private GraphQLTester tester;

    @Autowired
    private EntityCreator entityCreator;

    @Test
    @Transactional
    public void testRegister() throws Exception {
        tester.setUser(entityCreator.createUser("dr6"));
        String mutation = tester.readResource("graphql/register.graphql");
        Map<String, Map<String, Map<String, List<Map<String, String>>>>> result = tester.post(mutation);
        assertNotNull(result.get("data").get("register").get("labware").get(0).get("barcode"));
        assertEquals(result.get("data").get("register").get("tissue").get(0).get("externalName"), "TISSUE1");
    }

    @Test
    @Transactional
    public void testPlanOperation() throws Exception {
        tester.setUser(entityCreator.createUser("dr6"));
        Sample sample = entityCreator.createSample(entityCreator.createTissue(entityCreator.createDonor("DONOR1", LifeStage.adult), "TISSUE1"), null);
        entityCreator.createBlock("STAN-B70C", sample);
        String mutation = tester.readResource("graphql/plan.graphql");
        mutation = mutation.replace("$sampleId", String.valueOf(sample.getId()));
        Map<String, Map<String, Map<String, List<Map<String, String>>>>> result = tester.post(mutation);
        assertNotNull(result.get("data").get("plan").get("labware").get(0).get("barcode"));
    }
}
