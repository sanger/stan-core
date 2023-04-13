package uk.ac.sanger.sccp.stan.integrationtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.service.store.StorelightClient;
import uk.ac.sanger.sccp.utils.GraphQLClient.GraphQLResponse;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

        GraphQLResponse storeResponse = new GraphQLResponse(objectMapper.createObjectNode().set("storeBarcode", storedItemNode), null);
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

        GraphQLResponse locationResponse = new GraphQLResponse(objectMapper.createObjectNode().set("location", locationNode), null);
        when(mockStorelightClient.postQuery(ArgumentMatchers.contains("location("), any())).thenReturn(locationResponse);

        result = tester.post("query { labwareInLocation(locationBarcode: \"STO-4\") { barcode, slots { samples { id }}}}");

        List<Map<String, ?>> labwareListData = chainGet(result, "data", "labwareInLocation");
        assertThat(labwareListData).hasSize(1);
        Map<String, ?> labwareData = labwareListData.get(0);
        assertEquals("STAN-100", labwareData.get("barcode"));
        assertEquals(sample.getId(), chainGet(labwareData, "slots", 0, "samples", 0, "id"));
    }

    @Transactional
    @Test
    public void testStoreMulti() throws Exception {
        User user = entityCreator.createUser("user1");
        tester.setUser(user);
        Sample sample = entityCreator.createSample(entityCreator.createTissue(entityCreator.createDonor("DONOR1"), "EXT1"), 10);
        LabwareType lt = entityCreator.createLabwareType("lt", 1, 1);
        entityCreator.createLabware("STAN-100", lt, sample);
        entityCreator.createLabware("STAN-101", lt, sample);
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode locationNode = objectMapper.createObjectNode()
                .put("id", 4)
                .put("barcode", "STO-4")
                .put("name", "Location 4")
                .set("stored", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("barcode", "STAN-100")
                                .put("address", "A1"))
                        .add(objectMapper.createObjectNode()
                                .put("barcode", "STAN-101"))
                );

        GraphQLResponse storeResponse = new GraphQLResponse(
                objectMapper.createObjectNode().set("store", objectMapper.createObjectNode().put("numStored", 3)),
                null
        );

        GraphQLResponse locationResponse = new GraphQLResponse(
                objectMapper.createObjectNode().set("location", locationNode),
                null
        );

        when(mockStorelightClient.postQuery(ArgumentMatchers.contains("STAN-100"), any())).thenReturn(storeResponse);

        when(mockStorelightClient.postQuery(ArgumentMatchers.contains("location(location:"), any())).thenReturn(locationResponse);

        Map<String, ?> result = tester.post("mutation { store(store:[{barcode:\"STAN-100\", address:\"A1\"},{barcode:\"STAN-101\",address:\"A2\"}]," +
                "locationBarcode:\"STO-4\") { barcode, stored { barcode, address } }}");

        assertNull(result.get("errors"));
        assertEquals("STO-4", chainGet(result, "data", "store", "barcode"));

        verifyStorelightQuery(mockStorelightClient, List.of("STAN-100", "STAN-101", "STO-4"), user.getUsername());
    }

    @Transactional
    @Test
    public void testTransfer() throws Exception {
        User user = entityCreator.createUser("user1");
        tester.setUser(user);
        Sample sample = entityCreator.createSample(entityCreator.createTissue(
                entityCreator.createDonor("DONOR1"), "EXT1"), 10);
        LabwareType lt = entityCreator.createLabwareType("lt", 1, 1);
        entityCreator.createLabware("STAN-100", lt, sample);
        entityCreator.createLabware("STAN-101", lt, sample);
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode sourceNode = objectMapper.createObjectNode()
                .put("id", 4)
                .put("barcode", "STO-4")
                .put("name", "Location 4")
                .set("stored", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("barcode", "STAN-100")
                                .put("address", "A1"))
                        .add(objectMapper.createObjectNode()
                                .put("barcode", "STAN-101"))
                );

        GraphQLResponse sourceResponse = new GraphQLResponse(
                objectMapper.createObjectNode().set("location", sourceNode),
                null
        );

        when(mockStorelightClient.postQuery(ArgumentMatchers.contains("location(location: {barcode:\"STO-4\""), any()))
                .thenReturn(sourceResponse);

        ObjectNode destinationNode = objectMapper.createObjectNode()
                .put("id", 5)
                .put("barcode", "STO-5")
                .put("name", "Location 5")
                .set("stored", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("barcode", "STAN-100")
                                .put("address", "A1"))
                        .add(objectMapper.createObjectNode()
                                .put("barcode", "STAN-101"))
                );

        GraphQLResponse destinationResponse = new GraphQLResponse(
                objectMapper.createObjectNode().set("location", destinationNode),
                null
        );

        when(mockStorelightClient.postQuery(ArgumentMatchers.contains("location(location: {barcode:\"STO-5\""), any()))
                .thenReturn(destinationResponse);

        GraphQLResponse storeResponse = new GraphQLResponse(
                objectMapper.createObjectNode().set("store", objectMapper.createObjectNode().put("numStored", 2)),
                null
        );
        when(mockStorelightClient.postQuery(ArgumentMatchers.contains("store("), any())).thenReturn(storeResponse);

        Object response = tester.post("mutation { transfer(sourceBarcode: \"STO-4\", destinationBarcode: \"STO-5\")" +
                " { barcode, stored { barcode, address}}}");

        assertEquals("STO-5", chainGet(response, "data", "transfer", "barcode"));

        verifyStorelightQuery(mockStorelightClient, List.of("store", "STAN-100", "STAN-101", "STO-5"), user.getUsername());
    }

    @Transactional
    @Test
    public void testStoragePath() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode parent = objectMapper.createObjectNode()
                .put("barcode", "STO-P")
                .put("name", "Alpha: Beta")
                .putNull("address");
        ObjectNode child = objectMapper.createObjectNode()
                .put("barcode", "STO-C")
                .put("name", "Gamma: Delta")
                .put("address", "C1");
        ArrayNode arrayNode = objectMapper.createArrayNode()
                .add(parent)
                .add(child);
        GraphQLResponse graphQLResponse = new GraphQLResponse(
                objectMapper.createObjectNode().set("locationHierarchy", arrayNode), null
        );
        when(mockStorelightClient.postQuery(anyString(), any())).thenReturn(graphQLResponse);

        Object response = tester.post("query { storagePath(locationBarcode: \"STO-C\") {" +
                " barcode fixedName customName address } }");
        List<Map<String, String>> locs = chainGet(response, "data", "storagePath");
        assertThat(locs).hasSize(2);
        assertMap(locs.get(0), "barcode", "STO-P", "fixedName", "Alpha", "customName", "Beta", "address", null);
        assertMap(locs.get(1), "barcode", "STO-C", "fixedName", "Gamma", "customName", "Delta", "address", "C1");

        verifyStorelightQuery(mockStorelightClient, List.of("locationHierarchy", "STO-C"), null);
    }

    private static void assertMap(Map<String, String> map, String... kvs) {
        final int len = kvs.length;
        for (int i = 0; i < len; i += 2) {
            assertEquals(kvs[i+1], map.get(kvs[i]), kvs[i]);
        }
        assertThat(map).hasSize(kvs.length/2);
    }
}
