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
import uk.ac.sanger.sccp.stan.EntityFactory;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.store.*;
import uk.ac.sanger.sccp.stan.repo.LabwareRepo;
import uk.ac.sanger.sccp.stan.request.StoreInput;
import uk.ac.sanger.sccp.stan.service.EmailService;
import uk.ac.sanger.sccp.utils.GraphQLClient.GraphQLResponse;
import uk.ac.sanger.sccp.utils.UCMap;

import javax.persistence.EntityNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
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
    private LabwareRepo mockLabwareRepo;
    private EmailService mockEmailService;

    @BeforeEach
    void setup() throws IOException {
        mockClient = mock(StorelightClient.class);
        mockLabwareRepo = mock(LabwareRepo.class);
        mockEmailService = mock(EmailService.class);
        user = new User("dr6", User.Role.normal);
        service = spy(new StoreService(mockClient, mockLabwareRepo, mockEmailService));
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

    @Test
    public void testStore_none() throws IOException {
        String locationBarcode = "STO-123";
        Location location = new Location();
        location.setBarcode(locationBarcode);
        doReturn(location).when(service).getLocation(locationBarcode);
        User user = EntityFactory.getUser();
        List<StoreInput> storeInputs = List.of();
        assertSame(location, service.store(user, storeInputs, locationBarcode));
        verify(service, never()).validateLabwareBarcodesForStorage(any());
        verifyNoInteractions(mockClient);
    }

    @Test
    public void testStore() throws IOException {
        String locationBarcode = "STO-123";
        Location location = new Location();
        location.setBarcode(locationBarcode);
        doReturn(location).when(service).getLocation(locationBarcode);
        List<StoreInput> storeInputs = List.of(
                new StoreInput("STAN-01", new Address(1,1)),
                new StoreInput("STAN-02", null)
        );
        doNothing().when(service).validateLabwareBarcodesForStorage(any());

        GraphQLResponse response = setupResponse("store", 3);

        assertSame(location, service.store(user, storeInputs, locationBarcode));

        verify(service).validateLabwareBarcodesForStorage(List.of("STAN-01", "STAN-02"));
        verify(service).serialiseStoreInputs(storeInputs);
        verifyQueryMatches("mutation{store(store:[{barcode:\"STAN-01\",address:\"A1\"},{barcode:\"STAN-02\"},]," +
                "location:{barcode:\"STO-123\"}){ numStored }}", user.getUsername());
        verify(service).checkErrors(response);
        verify(service).getLocation(locationBarcode);
    }

    @Test
    public void testSerialiseStoreInputs() throws JsonProcessingException {
        Address A1 = new Address(1,1);
        Address A2 = new Address(1,2);
        List<StoreInput> storeInputs = List.of(
                new StoreInput("STAN-01", A1), new StoreInput("STAN-02", A2),
                new StoreInput("STAN-03", null)
        );
        String serialised = service.serialiseStoreInputs(storeInputs);
        String expected = "[{barcode:\"STAN-01\",address:\"A1\"},{barcode:\"STAN-02\",address:\"A2\"},{barcode:\"STAN-03\"},]";
        assertEquals(expected, serialised.replace(" ",""));
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testStoreBarcode(boolean withAddress) throws IOException {
        Address address = withAddress ? new Address(2,3) : null;
        Labware lw = EntityFactory.makeLabware(EntityFactory.getTubeType(), EntityFactory.getSample());
        String itemBarcode = lw.getBarcode();
        when(mockLabwareRepo.getByBarcode(itemBarcode)).thenReturn(lw);
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

        verify(service).validateLabwareBarcodeForStorage(itemBarcode);

        verifyQueryMatches("mutation { storeBarcode(barcode: \""+itemBarcode+"\", location: {barcode: \""
                + locationBarcode+"\"}, address: " + quote(address) + ") { barcode address location " +
                "{ id barcode name address size { numRows numColumns } " +
                "children { barcode name address } " +
                "stored { barcode address }" +
                "parent { barcode name address }" +
                "direction}}}");
        verify(service).checkErrors(response);
        assertEquals(item, result);
    }

    @ParameterizedTest
    @MethodSource("unstoreBarcodeArgs")
    public void testUnstoreBarcode(boolean itemExists, boolean itemStored) throws IOException {
        String barcode = "ITEM-1";
        when(mockLabwareRepo.existsByBarcode(barcode)).thenReturn(itemExists);

        if (!itemExists) {
            assertThat(assertThrows(EntityNotFoundException.class, () -> service.unstoreBarcode(user, barcode)))
                    .hasMessage("No labware found with barcode " + repr(barcode));
            verifyNoInteractions(mockClient);
            return;
        }

        UnstoredItem item = itemStored ? new UnstoredItem(barcode, new Address(2,3)) : null;

        GraphQLResponse response = setupResponse("unstoreBarcode", item);
        UnstoredItem result = service.unstoreBarcode(user, barcode);
        verifyQueryMatches("mutation { unstoreBarcode(barcode: \""+barcode+"\") {barcode address}}");
        verify(service).checkErrors(response);
        assertEquals(item, result);
    }

    static Stream<Arguments> unstoreBarcodeArgs() {
        return Stream.of(Arguments.of(false, false), Arguments.of(true, false), Arguments.of(true, true));
    }

    @Test
    public void testUnstoreBarcodesWithoutValidatingThem() throws IOException {
        assertEquals(0, service.unstoreBarcodesWithoutValidatingThem(user, List.of()));

        List<String> barcodes = List.of("STAN-001", "STAN-002");
        GraphQLResponse response = setupResponse("unstoreBarcodes", Map.of("numUnstored", 1));
        assertEquals(1, service.unstoreBarcodesWithoutValidatingThem(user, barcodes));
        verifyQueryMatches("mutation { unstoreBarcodes(barcodes: [\"STAN-001\",\"STAN-002\"]) { numUnstored }}");
        verify(service).checkErrors(response);
    }

    @ParameterizedTest
    @MethodSource("validateBarcodeForStorageArgs")
    public void testValidateBarcodeForStorage(Object labwareOrBarcode, String expectedErrorMessage) {
        if (labwareOrBarcode instanceof String) {
            String barcode = (String) labwareOrBarcode;
            when(mockLabwareRepo.getByBarcode(barcode)).thenThrow(new EntityNotFoundException(expectedErrorMessage));
            assertThat(assertThrows(EntityNotFoundException.class, () -> service.validateLabwareBarcodeForStorage(barcode)))
                    .hasMessage(expectedErrorMessage);
            return;
        }
        Labware labware = (Labware) labwareOrBarcode;
        String barcode = labware.getBarcode();
        when(mockLabwareRepo.getByBarcode(barcode)).thenReturn(labware);
        if (expectedErrorMessage==null) {
            service.validateLabwareBarcodeForStorage(barcode);
        } else {
            expectedErrorMessage = expectedErrorMessage.replace("%bc", barcode);
            assertThat(assertThrows(IllegalArgumentException.class, () -> service.validateLabwareBarcodeForStorage(barcode)))
                    .hasMessage(expectedErrorMessage);
        }
    }

    static Stream<Arguments> validateBarcodeForStorageArgs() {
        LabwareType lt = EntityFactory.getTubeType();
        Sample sample = EntityFactory.getSample();
        Labware okLabware = EntityFactory.makeLabware(lt, sample);
        Labware emptyLabware = EntityFactory.makeEmptyLabware(lt);
        Labware releasedLabware = EntityFactory.makeEmptyLabware(lt);
        releasedLabware.setReleased(true);
        Labware destroyedLabware = EntityFactory.makeEmptyLabware(lt);
        destroyedLabware.setDestroyed(true);
        Labware discardedLabware = EntityFactory.makeEmptyLabware(lt);
        discardedLabware.setDiscarded(true);
        Labware usedLabware = EntityFactory.makeLabware(lt, sample);
        usedLabware.setUsed(true);

        return Stream.of(
                Arguments.of("Bananas", "No labware found with barcode \"Bananas\""),

                Arguments.of(okLabware, null),
                Arguments.of(usedLabware, null),
                Arguments.of(releasedLabware, "Labware %bc cannot be stored because it is released."),
                Arguments.of(destroyedLabware, "Labware %bc cannot be stored because it is destroyed."),
                Arguments.of(discardedLabware, "Labware %bc cannot be stored because it is discarded."),
                Arguments.of(emptyLabware, "Labware %bc cannot be stored because it is empty.")
        );
    }

    @ParameterizedTest
    @MethodSource("validateBarcodesForStorageArgs")
    public void testValidateBarcodesForStorage(List<String> barcodes, UCMap<Labware> labware, String expectedErrorMessage) {
        if (labware==null || labware.isEmpty()) {
            when(mockLabwareRepo.findByBarcodeIn(any())).thenReturn(List.of());
        } else {
            when(mockLabwareRepo.findByBarcodeIn(any())).then(invocation -> {
                Collection<String> bcs = invocation.getArgument(0);
                return bcs.stream().map(labware::get).filter(Objects::nonNull).collect(toList());
            });
        }
        if (expectedErrorMessage==null) {
            service.validateLabwareBarcodesForStorage(barcodes);
        } else {
            assertThat(assertThrows(RuntimeException.class,
                    () -> service.validateLabwareBarcodesForStorage(barcodes)))
                    .hasMessage(expectedErrorMessage);
        }
    }

    static Stream<Arguments> validateBarcodesForStorageArgs() {
        LabwareType lt = EntityFactory.getTubeType();
        Sample sample = EntityFactory.getSample();
        Labware okLabware = EntityFactory.makeLabware(lt, sample);
        okLabware.setBarcode("STAN-OK");
        Labware usedLabware = EntityFactory.makeLabware(lt, sample);
        usedLabware.setBarcode("STAN-USED");
        usedLabware.setUsed(true);
        Labware emptyLabware = EntityFactory.makeEmptyLabware(lt);
        emptyLabware.setBarcode("STAN-EMPTY");
        Labware releasedLabware = EntityFactory.makeEmptyLabware(lt);
        releasedLabware.setReleased(true);
        releasedLabware.setBarcode("STAN-REL");
        Labware destroyedLabware = EntityFactory.makeEmptyLabware(lt);
        destroyedLabware.setDestroyed(true);
        destroyedLabware.setBarcode("STAN-DES");
        Labware discardedLabware = EntityFactory.makeEmptyLabware(lt);
        discardedLabware.setBarcode("STAN-DIS");
        discardedLabware.setDiscarded(true);

        UCMap<Labware> labware = UCMap.from(Labware::getBarcode, okLabware, emptyLabware, releasedLabware,
                destroyedLabware, discardedLabware, usedLabware);

        return Arrays.stream(new Object[][] {
                {okLabware, null},
                {usedLabware, null},
                {emptyLabware, "Labware [STAN-EMPTY] cannot be stored because it is [empty]."},
                {releasedLabware, "Labware [STAN-REL] cannot be stored because it is [released]."},
                {destroyedLabware, "Labware [STAN-DES] cannot be stored because it is [destroyed]."},
                {discardedLabware, "Labware [STAN-DIS] cannot be stored because it is [discarded]."},
                {List.of("STAN-OK", "STAN-404"), "Unknown labware barcodes: [STAN-404]"},
                {List.of("stan-ok", "STAN-OK", "STAN-3"), "Repeated barcode given: STAN-OK"},
                {List.of("STAN-OK", "STAN-DIS", "stan-des", "stan-rel", "stan-empty"),
                  "Labware [STAN-DIS, STAN-DES, STAN-REL, STAN-EMPTY] cannot be stored because it is " +
                          "[discarded, destroyed, released, empty]."},
        }).map(arr -> Arguments.of(
                arr[0] instanceof List ? arr[0] : arr[0] instanceof Labware ? List.of(((Labware) arr[0]).getBarcode()) : List.of(arr[0]),
                labware, arr[1]
        ));
    }

    @ParameterizedTest
    @ValueSource(booleans={false, true})
    public void testDiscardStorage(boolean successful) {
        if (successful) {
            doReturn(0).when(service).unstoreBarcodesWithoutValidatingThem(any(), any());
        } else {
            doThrow(IllegalArgumentException.class).when(service).unstoreBarcodesWithoutValidatingThem(any(), any());
        }

        List<String> barcodes = List.of("STAN-A1", "STAN-B2");
        service.discardStorage(user, barcodes);
        verify(service).unstoreBarcodesWithoutValidatingThem(user, barcodes);
        if (!successful) {
            verify(mockEmailService).tryAndSendAlert(any(), any());
        }
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
        returnedNode.remove("fixedName");
        returnedNode.remove("customName");
        GraphQLResponse response = setupResponse("editLocation", returnedNode);
        Location result = service.setLocationCustomName(user, barcode, newCustomName);
        verifyQueryMatches("mutation { editLocation(location:{barcode:"+json(barcode)
                +"}, change: {name:"+json(alteredLocation.getName())+"}) {" +
                "id barcode name address size {numRows numColumns } " +
                "children { barcode name address }" +
                "stored { barcode address } " +
                "parent { barcode name address }" +
                "direction }}");
        verify(service).checkErrors(response);
        assertEquals(alteredLocation, result);
        assertEquals(newCustomName, alteredLocation.getCustomName());
        assertEquals(name, alteredLocation.getFixedName());
    }

    @Test
    public void testGetLocation() throws IOException {
        String barcode = "STO-001F";
        Location location = new Location();
        location.setBarcode(barcode);
        location.setNameAndCustomName("Alpha", "Beta");
        location.setAddress(new Address(2,1));
        location.setDirection(GridDirection.RightDown);
        StoredItem item = new StoredItem();
        item.setBarcode("ITEM-1");
        item.setAddress(new Address(1,2));
        location.getStored().add(item);
        LinkedLocation parent = new LinkedLocation();
        parent.setBarcode("STO-000A");
        location.setParent(parent);
        LinkedLocation child = new LinkedLocation();
        child.setBarcode("STO-002E");
        child.setAddress(new Address(3,2));
        location.getChildren().add(child);

        ObjectNode returnedNode = objectMapper.valueToTree(location);
        returnedNode.remove("fixedName");
        returnedNode.remove("customName");
        GraphQLResponse response = setupResponse("location", returnedNode);
        Location result = service.getLocation(barcode);
        verifyQueryMatches("{" +
                        "    location(location: {barcode:\""+barcode+"\"}) {" +
                        "        id" +
                        "        barcode" +
                        "        name" +
                        "        address" +
                        "        size { numRows numColumns}" +
                        "        children { barcode name address }" +
                        "        stored { barcode address }" +
                        "        parent { barcode name address }" +
                        "        direction" +
                        "        qualifiedNameWithFirstBarcode" +
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
                "            id" +
                "            barcode" +
                "            name" +
                "            address" +
                "            size { numRows numColumns}" +
                "            children { barcode name address }" +
                "            parent { barcode name address }" +
                "            stored { barcode address }" +
                "            direction" +
                "            qualifiedNameWithFirstBarcode" +
                "        }}}",
                null);

        verify(service).checkErrors(response);
    }

    @Test
    public void testLoadBasicLocationsOfItems_empty() {
        assertThat(service.loadBasicLocationsOfItems(List.of())).isEmpty();
        verifyNoInteractions(mockClient);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testLoadBasicLocationsOfItems(boolean succeeds) throws IOException {
        UCMap<BasicLocation> locations;
        final List<String> stanBarcodes = List.of("STAN-1", "STAN-2", "STAN-3");
        if (succeeds) {
            ArrayNode itemsNode = objectMapper.createArrayNode();
            itemsNode.add(objectMapper.createObjectNode()
                            .put("barcode", "STAN-1")
                            .put("address", "A2")
                            .set("location", objectMapper.createObjectNode().put("barcode", "STO-1"))
                    )
                    .add(objectMapper.createObjectNode()
                            .put("barcode", "STAN-2")
                            .putNull("address")
                            .set("location", objectMapper.createObjectNode().put("barcode", "STO-2"))
                    );
            GraphQLResponse response = setupResponse("stored", itemsNode);
            when(mockClient.postQuery(anyString(), isNull())).thenReturn(response);
            locations = service.loadBasicLocationsOfItems(stanBarcodes);
            assertThat(locations).hasSize(2);
            assertEquals(new BasicLocation("STO-1", new Address(1,2)), locations.get("STAN-1"));
            assertEquals(new BasicLocation("STO-2", null), locations.get("STAN-2"));
            assertNull(locations.get("STAN-3"));
            verify(service).checkErrors(response);
        } else {
            final IOException ioException = new IOException("Everything is bad.");
            doThrow(ioException).when(mockClient).postQuery(anyString(), isNull());
            var ex = assertThrows(UncheckedIOException.class, () -> service.loadBasicLocationsOfItems(stanBarcodes));
            assertThat(ex).hasCause(ioException);
        }

        verifyQueryMatches("{" +
                " stored(barcodes: [\"STAN-1\", \"STAN-2\", \"STAN-3\"]) {" +
                "  barcode" +
                "  address" +
                "  location { barcode }" +
                "}}", null);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testGetLabwareInLocation(boolean any) {
        Location loc = new Location();
        final String locBarcode = "STO-2000";
        loc.setBarcode(locBarcode);
        List<Labware> labware;
        List<StoredItem> storedItems;
        List<String> labwareBarcodes;
        if (any) {
            Labware lw1 = EntityFactory.getTube();
            Labware lw2 = EntityFactory.makeLabware(lw1.getLabwareType(), EntityFactory.getSample());
            labware = List.of(lw1, lw2);
            storedItems = List.of(
                    new StoredItem(lw1.getBarcode(), loc, new Address(1,2)),
                    new StoredItem(lw2.getBarcode(), loc),
                    new StoredItem("Other", loc)
            );
            when(mockLabwareRepo.findByBarcodeIn(any())).thenReturn(labware);
            labwareBarcodes = List.of(lw1.getBarcode(), lw2.getBarcode(), "Other");
        } else {
            labware = List.of();
            storedItems = List.of();
            labwareBarcodes = List.of();
        }
        loc.setStored(storedItems);
        doReturn(loc).when(service).getLocation(locBarcode);

        assertSame(labware, service.getLabwareInLocation(locBarcode));

        verify(service).getLocation(locBarcode);
        if (any) {
            verify(mockLabwareRepo).findByBarcodeIn(labwareBarcodes);
        } else {
            verifyNoInteractions(mockLabwareRepo);
        }
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
