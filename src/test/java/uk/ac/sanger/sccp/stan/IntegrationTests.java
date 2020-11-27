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
        String mutation = "mutation {\n" +
                "  register(request:{\n" +
                "    blocks:[\n" +
                "      {\n" +
                "        labwareType:\"proviasette\",\n" +
                "        donorIdentifier:\"DONOR1\",\n" +
                "        externalIdentifier:\"TISSUE1\",\n" +
                "        lifeStage:adult,\n" +
                "        hmdmc:\"20/0002\",\n" +
                "        spatialLocation:0,\n" +
                "        tissueType:\"Arm\",\n" +
                "        replicateNumber:1,\n" +
                "        medium:\"None\",\n" +
                "        mouldSize:\"None\",\n" +
                "        fixative:\"None\",\n" +
                "        highestSection:0,\n" +
                "      }\n" +
                "    ]\n" +
                "  }) {\n" +
                "    labware {\n" +
                "      barcode\n" +
                "    }\n" +
                "    tissue {\n" +
                "      externalName\n" +
                "    }\n" +
                "  }\n" +
                "}";
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
        String mutation = "mutation {\n" +
                "  plan(request:{\n" +
                "    operationType: \"Section\",\n" +
                "    labware:[{\n" +
                "      labwareType:\"Tube\",\n" +
                "      actions:[{\n" +
                "        source:{\n" +
                "          barcode:\"STAN-B70C\",\n" +
                "        },\n" +
                "        address:{row:1, column:1},\n" +
                "        sampleId:"+sample.getId()+",\n" +
                "      }],\n" +
                "    }]\n" +
                "  }) {\n" +
                "    labware {\n" +
                "      barcode\n" +
                "    }\n" +
                "  }\n" +
                "}";
        Map<String, Map<String, Map<String, List<Map<String, String>>>>> result = tester.post(mutation);
        assertNotNull(result.get("data").get("plan").get("labware").get(0).get("barcode"));
    }
}
