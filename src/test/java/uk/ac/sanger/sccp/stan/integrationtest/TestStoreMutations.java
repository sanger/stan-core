package uk.ac.sanger.sccp.stan.integrationtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.sanger.sccp.stan.EntityCreator;
import uk.ac.sanger.sccp.stan.GraphQLTester;
import uk.ac.sanger.sccp.stan.model.Sample;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.service.store.StorelightClient;
import uk.ac.sanger.sccp.utils.GraphQLClient;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.chainGet;
import static uk.ac.sanger.sccp.stan.integrationtest.IntegrationTestUtils.verifyStorelightQuery;

/**
 * Tests the store mutation
 * @author dr6
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({GraphQLTester.class, EntityCreator.class})
public class TestStoreMutations {
    @Autowired
    private GraphQLTester tester;
    @Autowired
    private EntityCreator entityCreator;

    @MockBean
    StorelightClient mockStorelightClient;

    @Transactional
    @Test
    public void testStore() throws Exception {
        // 1. storeBarcode

        User user = entityCreator.createUser("user1");
        tester.setUser(user);
        Sample sample = entityCreator.createSample(entityCreator.createTissue(entityCreator.createDonor("DONOR1"), "EXT1"), 10);
        entityCreator.createLabware("STAN-100", entityCreator.createLabwareType("lw1", 1, 1), sample);
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode storedItemNode = objectMapper.createObjectNode()
                .put("barcode", "STAN-100")
                .set("location", objectMapper.createObjectNode()
                        .put("id", 4)
                        .put("barcode", "STO-4")
                );

        GraphQLClient.GraphQLResponse storeResponse = new GraphQLClient.GraphQLResponse(objectMapper.createObjectNode().set("storeBarcode", storedItemNode), null);
        when(mockStorelightClient.postQuery(ArgumentMatchers.contains("storeBarcode("), any())).thenReturn(storeResponse);

        Object result = tester.post("mutation { storeBarcode(barcode: \"STAN-100\", locationBarcode: \"STO-4\") { barcode }}");
        assertEquals("STAN-100", chainGet(result, "data", "storeBarcode", "barcode"));

        verifyStorelightQuery(mockStorelightClient, List.of("STAN-100", "STO-4"), user.getUsername());

        // 2. labwareInLocation

        ObjectNode locationNode = objectMapper.createObjectNode()
                .put("id", 4)
                .put("barcode", "STO-4")
                .put("name", "Location 4")
                .set("stored", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("barcode", "STAN-100"))
                        .add(objectMapper.createObjectNode()
                                .put("barcode", "Other"))
                );

        GraphQLClient.GraphQLResponse locationResponse = new GraphQLClient.GraphQLResponse(objectMapper.createObjectNode().set("location", locationNode), null);
        when(mockStorelightClient.postQuery(ArgumentMatchers.contains("location("), any())).thenReturn(locationResponse);

        result = tester.post("query { labwareInLocation(locationBarcode: \"STO-4\") { barcode, slots { samples { id }}}}");

        List<Map<String, ?>> labwareListData = chainGet(result, "data", "labwareInLocation");
        assertThat(labwareListData).hasSize(1);
        Map<String, ?> labwareData = labwareListData.get(0);
        assertEquals("STAN-100", labwareData.get("barcode"));
        assertEquals(sample.getId(), chainGet(labwareData, "slots", 0, "samples", 0, "id"));
    }
}