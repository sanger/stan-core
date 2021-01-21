package uk.ac.sanger.sccp.stan.service.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.model.store.*;
import uk.ac.sanger.sccp.utils.GraphQLClient.GraphQLResponse;

import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests {@link StoreService}
 * @author dr6
 */
public class TestStoreService {
    private StorelightClient mockClient;
    private StoreService service;
    private User user;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() throws IOException {
        mockClient = mock(StorelightClient.class);
        user = new User("dr6");
        service = spy(new StoreService(mockClient));
        objectMapper = new ObjectMapper();
    }

    GraphQLResponse setupResponse(GraphQLResponse response) throws IOException {
        when(mockClient.postQuery(anyString(), any())).thenReturn(response);
        return response;
    }

    GraphQLResponse setupResponse(String operationName, Object object) throws IOException {
        JsonNode jsonNode;
        if (object instanceof JsonNode) {
            jsonNode = (JsonNode) object;
        } else {
            jsonNode = objectMapper.valueToTree(object);
        }
        ObjectNode dataNode = objectMapper.createObjectNode();
        dataNode.set(operationName, jsonNode);
        return setupResponse(new GraphQLResponse(dataNode, null));
    }

    private static String quote(Address address) {
        if (address==null) {
            return "null";
        }
        return "\"" + address + "\"";
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testStoreBarcode(boolean withAddress) throws IOException {
        Address address = withAddress ? new Address(2,3) : null;
        String itemBarcode = "STAN-123";
        String locationBarcode = "STO-ABC";
        StoredItem item = new StoredItem();
        item.setBarcode(itemBarcode);
        item.setAddress(address);
        Location location = new Location();
        item.setLocation(location);
        location.setBarcode(locationBarcode);
        StoredItem subItem = new StoredItem();
        subItem.setBarcode(itemBarcode);
        subItem.setAddress(address);
        location.getStored().add(subItem);

        GraphQLResponse response = setupResponse("storeBarcode", item);

        StoredItem result = service.storeBarcode(user, itemBarcode, locationBarcode, address);

        verifyQueryMatches("mutation { storeBarcode(barcode: \""+itemBarcode+"\", location: {barcode: \""
                + locationBarcode+"\"}, address: " + quote(address) + ") { barcode address location " +
                "{ barcode description address size { numRows numColumns } " +
                "children { barcode description address size { numRows numColumns }} " +
                "stored { barcode address }" +
                "parent { barcode description address size { numRows numColumns }}}}}");
        verify(service).checkErrors(response);
        assertEquals(item, result);
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testUnstoreBarcode(boolean itemExists) throws IOException {
        String barcode = "ITEM-1";
        UnstoredItem item = itemExists ? new UnstoredItem(barcode, new Address(2,3)) : null;

        GraphQLResponse response = setupResponse("unstoreBarcode", item);
        UnstoredItem result = service.unstoreBarcode(user, barcode);
        verifyQueryMatches("mutation { unstoreBarcode(barcode: \""+barcode+"\") {barcode address}}");
        verify(service).checkErrors(response);
        assertEquals(item, result);
    }

    @Test
    public void testEmpty() throws IOException {
        String locationBarcode = "STO-ABC";
        UnstoreResult expected = new UnstoreResult(List.of(new UnstoredItem("ITEM-1", null), new UnstoredItem("ITEM-2", new Address(1, 2))));
        ObjectNode expectedNode = objectMapper.valueToTree(expected);
        expectedNode.remove("numUnstored");
        GraphQLResponse response = setupResponse("empty", expectedNode);
        UnstoreResult result = service.empty(user, locationBarcode);
        verifyQueryMatches("mutation { empty(location: { barcode: \""+locationBarcode+"\"}) { unstored { barcode address}}}");
        verify(service).checkErrors(response);
        assertEquals(expected, result);
    }

    @ParameterizedTest
    @MethodSource("setCustomNameArgs")
    public void testSetLocationCustomName(String name, String oldCustomName, String newCustomName) throws IOException {
        String barcode = "STO-001F";
        Location oldLocation = new Location();
        oldLocation.setBarcode(barcode);
        oldLocation.setNameAndCustomName(name, oldCustomName);

        doReturn(oldLocation).when(service).getLocation(barcode);

        Location alteredLocation = new Location();
        alteredLocation.setNameAndCustomName(name, newCustomName);
        ObjectNode returnedNode = objectMapper.valueToTree(alteredLocation);
        returnedNode.remove("name");
        returnedNode.remove("customName");
        GraphQLResponse response = setupResponse("editLocation", returnedNode);
        Location result = service.setLocationCustomName(user, barcode, newCustomName);
        verifyQueryMatches("mutation { editLocation(location:{barcode:"+json(barcode)
                +"}, change: {description:"+json(alteredLocation.getDescription())+"}) {" +
                "barcode description address size {numRows numColumns } " +
                "children { barcode description address size { numRows numColumns }}" +
                "stored { barcode address } " +
                "parent { barcode description address size { numRows numColumns }}}}");
        verify(service).checkErrors(response);
        assertEquals(alteredLocation, result);
        assertEquals(newCustomName, alteredLocation.getCustomName());
        assertEquals(name, alteredLocation.getName());
    }

    @Test
    public void testGetLocation() throws IOException {
        String barcode = "STO-001F";
        Location location = new Location();
        location.setBarcode(barcode);
        location.setNameAndCustomName("Alpha", "Beta");
        location.setAddress(new Address(2,1));
        StoredItem item = new StoredItem();
        item.setBarcode("ITEM-1");
        item.setAddress(new Address(1,2));
        location.getStored().add(item);
        LinkedLocation parent = new LinkedLocation();
        parent.setBarcode("STO-000A");
        parent.setSize(new Size(5,6));
        location.setParent(parent);
        LinkedLocation child = new LinkedLocation();
        child.setBarcode("STO-002E");
        child.setAddress(new Address(3,2));
        location.getChildren().add(child);

        ObjectNode returnedNode = objectMapper.valueToTree(location);
        returnedNode.remove("name");
        returnedNode.remove("customName");
        GraphQLResponse response = setupResponse("location", returnedNode);
        Location result = service.getLocation(barcode);
        verifyQueryMatches("{" +
                        "    location(location: {barcode:\""+barcode+"\"}) {" +
                        "        barcode" +
                        "        description" +
                        "        address" +
                        "        size { numRows numColumns}" +
                        "        children { barcode description address size { numRows numColumns } }" +
                        "        stored { barcode address }" +
                        "        parent { barcode description address size { numRows numColumns } }" +
                        "    }}",

                null);
        verify(service).checkErrors(response);
        assertEquals(result, location);
        assertEquals(result.getParent(), parent);
        assertThat(result.getChildren()).containsOnly(child);
        item.setLocation(location);
        assertThat(result.getStored()).containsOnly(item);
    }

    @Test
    public void testGetStored() throws IOException {
        ArrayNode foundItems = objectMapper.createArrayNode();
        ObjectNode locationNode = objectMapper.createObjectNode()
                .put("barcode", "STO-001F")
                .set("stored", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode()
                                .put("barcode", "ITEM-1")
                                .put("address", "B3"))
                        .add(objectMapper.createObjectNode()
                                .put("barcode", "ITEM-2")));
        foundItems.add(objectMapper.createObjectNode()
                .put("barcode", "ITEM-1")
                .put("address", "B3")
                .set("location", locationNode))
                .add(objectMapper.createObjectNode()
                        .put("barcode", "ITEM-2")
                        .set("location", locationNode));
        GraphQLResponse response = setupResponse("stored", foundItems);

        List<StoredItem> results = service.getStored(List.of("ITEM-1", "ITEM-2", "ITEM-3"));
        assertThat(results).hasSize(2);
        List<StoredItem> expectedItems = IntStream.range(1, 3).mapToObj(i -> {
            StoredItem si = new StoredItem();
            si.setBarcode("ITEM-"+i);
            return si;
        }).collect(toList());
        expectedItems.get(0).setAddress(new Address(2,3));
        Location location = new Location();
        location.setBarcode("STO-001F");
        location.setStored(expectedItems);
        expectedItems.forEach(item -> item.setLocation(location));

        assertEquals(expectedItems, results);
        for (StoredItem item : results) {
            assertEquals(item.getLocation(), location);
            assertEquals(item.getLocation().getStored(), expectedItems);
        }
        
        verifyQueryMatches("{" +
                "    stored(barcodes: [\"ITEM-1\", \"ITEM-2\", \"ITEM-3\"]) {" +
                "        barcode" +
                "        address" +
                "        location {" +
                "            barcode" +
                "            description" +
                "            address" +
                "            size { numRows numColumns}" +
                "            children { barcode description address size { numRows numColumns } }" +
                "            parent { barcode description address size { numRows numColumns } }" +
                "            stored { barcode address }" +
                "        }}}",
                null);

        verify(service).checkErrors(response);
    }

    @Test
    public void testCheckErrors() {
        GraphQLResponse data = new GraphQLResponse(objectMapper.createObjectNode(), null);
        assertSame(data, service.checkErrors(data));

        String errorMessage = "Everything is bad.";
        GraphQLResponse errors = new GraphQLResponse(null, objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                .put("message", errorMessage)));
        assertThat(assertThrows(StoreException.class, () -> service.checkErrors(errors)))
                .hasMessage(errorMessage);
    }

    private String json(String string) throws JsonProcessingException {
        return objectMapper.writeValueAsString(string);
    }

    static Stream<Arguments> setCustomNameArgs() {
        String[] values = {
                null, null, null,
                null, null, "Alpha",
                null, "Alpha", null,
                "Alpha", null, null,
                "Alpha", "Beta", "Gamma",
                "Alpha", "Spl\"foo\"boo", "Zop\"x-y",
        };
        return IntStream.range(0, values.length/3).map(i -> 3*i)
                .mapToObj(i -> Arguments.of(values[i], values[i+1], values[i+2]));
    }

    private void verifyQueryMatches(String expected) throws IOException {
        verifyQueryMatches(expected, user.getUsername());
    }

    private void verifyQueryMatches(String expected, String username) throws IOException {
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockClient).postQuery(queryCaptor.capture(), eq(username));
        String actual = queryCaptor.getValue();
        assertEquals(expected.replaceAll("\\s+", ""), actual.replaceAll("\\s+",""));
    }

}
