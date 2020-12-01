package uk.ac.sanger.sccp.stan;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.model.*;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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
        assertEquals("TISSUE1", result.get("data").get("register").get("tissue").get(0).get("externalName"));
    }

    @Test
    @Transactional
    public void testPlanOperation() throws Exception {
        tester.setUser(entityCreator.createUser("dr6"));
        Sample sample = entityCreator.createSample(entityCreator.createTissue(entityCreator.createDonor("DONOR1", LifeStage.adult), "TISSUE1"), null);
        Labware sourceBlock = entityCreator.createBlock("STAN-B70C", sample);
        String mutation = tester.readResource("graphql/plan.graphql");
        mutation = mutation.replace("$sampleId", String.valueOf(sample.getId()));
        Map<String, ?> result = tester.post(mutation);
        assertNull(result.get("errors"));
        Object resultPlan = chainGet(result, "data", "plan");
        List<?> resultLabware = chainGet(resultPlan, "labware");
        assertEquals(1, resultLabware.size());
        assertNotNull(chainGet(resultLabware, 0, "barcode"));
        assertEquals("Tube", chainGet(resultLabware, 0, "labwareType", "name"));
        List<?> resultOps = chainGet(resultPlan, "operations");
        assertEquals(resultOps.size(), 1);
        assertEquals("Section", chainGet(resultOps, 0, "operationType", "name"));
        List<?> resultActions = chainGet(resultOps, 0, "planActions");
        assertEquals(1, resultActions.size());
        Map<String, ?> resultAction = chainGet(resultActions, 0);
        Map<String, Integer> firstAddress = Map.of("row", 1, "column", 1);
        assertEquals(firstAddress, chainGet(resultAction, "source", "address"));
        assertEquals(sourceBlock.getId(), chainGet(resultAction, "source", "labwareId"));
        assertEquals(firstAddress, chainGet(resultAction, "destination", "address"));
        assertNotNull(chainGet(resultAction, "destination", "labwareId"));
        assertEquals(sample.getId(), chainGet(resultAction, "sample", "id"));
        assertNotNull(chainGet(resultAction, "newSection"));
    }

    @SuppressWarnings("unchecked")
    private static <T> T chainGet(Object container, Object... accessors) {
        for (Object accessor : accessors) {
            if (accessor instanceof Integer) {
                container = ((List<?>) container).get((int) accessor);
            } else {
                container = ((Map<?,?>) container).get(accessor);
            }
        }
        return (T) container;
    }
}
