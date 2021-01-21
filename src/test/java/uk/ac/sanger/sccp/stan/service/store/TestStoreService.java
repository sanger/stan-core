package uk.ac.sanger.sccp.stan.service.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.model.store.Location;
import uk.ac.sanger.sccp.stan.model.store.StoredItem;
import uk.ac.sanger.sccp.utils.GraphQLClient.GraphQLResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

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
        when(mockClient.postQuery(anyString(), anyString())).thenReturn(response);
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
                "{ barcode description address size { numRows , numColumns } " +
                "children { barcode, description, address, size { numRows , numColumns }} " +
                "stored { barcode , address }}}}");
        verify(service).checkErrors(response);
        assertEquals(item, result);
    }

    private void verifyQueryMatches(String expected) throws IOException {
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockClient).postQuery(queryCaptor.capture(), eq(user.getUsername()));
        String actual = queryCaptor.getValue();
        if (!actual.replaceAll("\\s+","").equals(expected.replaceAll("\\s+",""))) {
            fail("Expected query "+repr(expected)+" but got "+repr(actual));
        }
    }

}
