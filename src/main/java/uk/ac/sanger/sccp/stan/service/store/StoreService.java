package uk.ac.sanger.sccp.stan.service.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
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
import java.net.URL;
import java.util.*;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * Service for dealing with storage of labware
 * @author dr6
 */
@Service
public class StoreService {
    Logger log = LoggerFactory.getLogger(StoreService.class);

    private final StorelightClient storelightClient;
    private final LabwareRepo labwareRepo;
    private final ObjectMapper objectMapper;
    private final EmailService emailService;

    @Autowired
    public StoreService(StorelightClient storelightClient, LabwareRepo labwareRepo, EmailService emailService) {
        this.storelightClient = storelightClient;
        this.labwareRepo = labwareRepo;
        this.emailService = emailService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Stores multiple items (optionally at addresses) in a single location.
     * @param user the user responsible for the storage
     * @param storeInputs the specification of what to store (and the addresses)
     * @param locationBarcode the barcode of the location
     * @return the updated location from StoreLight
     * @exception UncheckedIOException if there is an io problem
     */
    public Location store(User user, List<StoreInput> storeInputs, String locationBarcode) {
        requireNonNull(user, "User is null.");
        requireNonNull(storeInputs, "Store inputs is null.");
        requireNonNull(locationBarcode, "Location barcode is null.");
        if (storeInputs.isEmpty()) {
            return getLocation(locationBarcode);
        }
        validateLabwareBarcodesForStorage(storeInputs.stream().map(StoreInput::getBarcode).collect(toList()));

        try {
            String query = readResource("store");
            query = query.replace("[{}]", serialiseStoreInputs(storeInputs));
            query = query.replace("\"LOCATIONBARCODE\"", objectMapper.writeValueAsString(locationBarcode));
            GraphQLResponse response = storelightClient.postQuery(query, user.getUsername());
            checkErrors(response);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return getLocation(locationBarcode);
    }

    /**
     * Converts a list of store inputs to a string suitable for insertion in a graphql request
     * @param storeInputs the store inputs to serialise
     * @return a string describing the store inputs
     * @exception JsonProcessingException there was a serialisation problem
     */
    public String serialiseStoreInputs(Collection<StoreInput> storeInputs) throws JsonProcessingException {
        StringBuilder sb = new StringBuilder();
        sb.append('[');

        for (StoreInput storeInput : storeInputs) {
            sb.append('{');
            sb.append("barcode: ").append(objectMapper.writeValueAsString(storeInput.getBarcode().toUpperCase()));
            if (storeInput.getAddress()!=null) {
                sb.append(", address: ").append(objectMapper.writeValueAsString(storeInput.getAddress().toString()));
            }
            sb.append("},");
        }

        return sb.append(']').toString();
    }

    /**
     * Stores a single item in a location
     * @param user the user responsible
     * @param barcode the barcode of the item to store
     * @param locationBarcode the barcode of the location
     * @param address the address to store at (or null)
     * @return the stored item
     */
    public StoredItem storeBarcode(User user, String barcode, String locationBarcode, Address address) {
        requireNonNull(user, "User is null.");
        requireNonNull(barcode, "Item barcode is null.");
        requireNonNull(locationBarcode, "Location barcode is null.");
        validateLabwareBarcodeForStorage(barcode);
        return send(user, "storeBarcode", new String[] {"\"BARCODE\"", "\"LOCATIONBARCODE\"", "\"ADDRESS\"" },
                new Object[] { barcode, locationBarcode, address}, StoredItem.class).fixInternalLinks();
    }

    /**
     * Unstores the specified item
     * @param user the user responsible
     * @param barcode the barcode of the item to unstore
     * @return the result of the unstorage
     */
    public UnstoredItem unstoreBarcode(User user, String barcode) {
        requireNonNull(user, "User is null.");
        requireNonNull(barcode, "Barcode is null.");
        if (!labwareRepo.existsByBarcode(barcode)) {
            throw new EntityNotFoundException("No labware found with barcode "+repr(barcode));
        }
        return send(user, "unstoreBarcode", new String[] { "\"BARCODE\"" }, new Object[] { barcode},
                    UnstoredItem.class);
    }

    /**
     * This is called when releasing labware. The barcodes have already been confirmed to be valid
     * labware barcodes, so they do not need to be looked up again; only unstored.
     * @param user the user responsible for unstoring the labware
     * @param barcodes the barcodes that need to be unstored
     * @return the number of items unstored
     */
    public int unstoreBarcodesWithoutValidatingThem(User user, Collection<String> barcodes) {
        requireNonNull(user, "User is null");
        requireNonNull(barcodes, "Barcodes is null");
        if (barcodes.isEmpty()) {
            return 0;
        }
        Map<?, ?> result = send(user, "unstoreBarcodes", new String[] { "[]" }, new Object[] { barcodes },
                Map.class);
        return (int) result.get("numUnstored");
    }

    /**
     * This is called after some operation is performed that renders labware unstorable.
     * Any corresponding labware that is stored becomes unstored. Any exception is caught and logged,
     * but the method still completes successfully.
     * This method {@link EmailService#tryAndSendAlert tries to send an alert email} if the request fails.
     * @param user the user responsible for the operation
     * @param barcodes the barcodes to unstore
     */
    public void discardStorage(User user, Collection<String> barcodes) {
        try {
            unstoreBarcodesWithoutValidatingThem(user, barcodes);
        } catch (RuntimeException e) {
            log.error("Caught exception during discardStorage, user: "+(user==null ? null : user.getUsername())
                    +", barcodes: "+barcodes, e);
            String serviceDescription = emailService.getServiceDescription();
            emailService.tryAndSendAlert(serviceDescription+" was unable to discard storage",
                    serviceDescription+" failed to discard storage for the following barcodes: "+barcodes);
        }
    }

    /**
     * Empties a specified location
     * @param user the user responsible
     * @param locationBarcode the barcode of the location to be emptied
     * @return the result of the unstoring of the items in the location
     */
    public UnstoreResult empty(User user, String locationBarcode) {
        requireNonNull(user, "User is null.");
        requireNonNull(locationBarcode, "Location barcode is null.");
        return send(user, "empty", new String[] { "\"LOCATIONBARCODE\"" },
                    new String[] { locationBarcode }, UnstoreResult.class);
    }

    /**
     * Sets the custom name of a location
     * @param user the user responsible
     * @param locationBarcode the barcode of the location
     * @param customName the new custom name
     * @return the updated location
     */
    public Location setLocationCustomName(User user, String locationBarcode, String customName) {
        requireNonNull(user, "User is null.");
        requireNonNull(locationBarcode, "Location barcode is null.");
        Location location = getLocation(locationBarcode);
        location.setCustomName(customName);
        return send(user, "editLocation",
                new String[] {"\"LOCATIONBARCODE\"", "\"NAME\""},
                new Object[] { location.getBarcode(), location.getName() },
                Location.class).fixInternalLinks();
    }

    /**
     * Gets the location with the given barcode
     * @param locationBarcode the barcode of the location
     * @return the specified location
     */
    public Location getLocation(String locationBarcode) {
        requireNonNull(locationBarcode, "Location barcode is null.");
        return send(null, "location", new String[] { "\"LOCATIONBARCODE\""},
                new Object[] { locationBarcode }, Location.class).fixInternalLinks();
    }

    public List<LinkedLocation> getHierarchy(String locationBarcode) {
        requireNonNull(locationBarcode, "Location barcode is null.");
        return send(null, "locationHierarchy", new String[]{"\"LOCATIONBARCODE\""},
                new Object[]{locationBarcode}, new TypeReference<>() {});
    }

    /**
     * Gets storage information about the given item barcodes
     * @param barcodes barcodes of stored items
     * @return the stored items
     */
    public List<StoredItem> getStored(Collection<String> barcodes) {
        requireNonNull(barcodes, "Barcodes collection is null.");
        if (barcodes.isEmpty()) {
            return List.of();
        }
        try {
            String query = readResource("stored");
            ArrayNode arrayNode = objectMapper.createArrayNode();
            for (String barcode: barcodes) {
                arrayNode.add(barcode);
            }
            query = query.replace("[]", objectMapper.writeValueAsString(arrayNode));
            GraphQLResponse response = storelightClient.postQuery(query, null);
            checkErrors(response);
            List<StoredItem> items = objectMapper.convertValue(response.getData().get("stored"),
                    new TypeReference<>() {});
            items.forEach(StoredItem::fixInternalLinks);
            return items;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Loads the basic location information for the specified items
     * @param itemBarcodes the barcodes of stored items
     * @return a map from each item barcode to its basic location
     */
    public UCMap<BasicLocation> loadBasicLocationsOfItems(Collection<String> itemBarcodes) {
        requireNonNull(itemBarcodes, "Barcode collection is null.");
        if (itemBarcodes.isEmpty()) {
            return new UCMap<>(0);
        }
        try {
            String query = readResource("storedBasicLocation");
            ArrayNode arrayNode = objectMapper.createArrayNode();
            for (String barcode : itemBarcodes) {
                arrayNode.add(barcode);
            }
            query = query.replace("[]", objectMapper.writeValueAsString(arrayNode));
            GraphQLResponse response = storelightClient.postQuery(query, null);
            checkErrors(response);
            var objectData = response.getData();
            ArrayNode storedData = (ArrayNode) objectData.get("stored");

            return makeBasicLocations(storedData);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Convert arraynodes into a map of item to basic location.
     * @param nodes a json array
     * @return a map of item barcode to its basic location
     */
    private UCMap<BasicLocation> makeBasicLocations(ArrayNode nodes) {
        UCMap<BasicLocation> map = new UCMap<>(nodes.size());
        for (JsonNode sd : nodes) {
            String itemBarcode = sd.get("barcode").textValue();
            if (itemBarcode==null || itemBarcode.isEmpty()) {
                continue;
            }
            var locationData = sd.get("location");
            if (!locationData.isObject()) {
                continue;
            }
            String locationBarcode = locationData.get("barcode").textValue();
            if (locationBarcode==null) {
                continue;
            }
            String locationName = locationData.get("name").textValue();
            String addressString = sd.get("address").textValue();
            Address address = null;
            if (addressString!=null && !addressString.isEmpty()) {
                address = Address.valueOf(addressString);
            }
            Integer addressIndex = integerFromNode(sd.get("addressIndex"));
            map.put(itemBarcode, new BasicLocation(locationBarcode, locationName, address, addressIndex));
        }
        return map;
    }

    /**
     * Reads a non-negative integer or null from the given node.
     * If the node is null, non-numeric, or has a negative value, returns null.
     * @param node the node to read
     * @return the non-negative integer value from the node, or null
     */
    private static Integer integerFromNode(JsonNode node) {
        if (node==null) {
            return null;
        }
        int value = node.asInt(-1);
        return (value < 0 ? null : (Integer) value);
    }

    /**
     * Checks if the labware can be stored.
     * @param barcode the barcode being stored
     * @exception EntityNotFoundException no such labware can be found
     * @exception IllegalArgumentException the specified labware cannot be stored
     */
    public void validateLabwareBarcodeForStorage(String barcode) {
        Labware lw = labwareRepo.getByBarcode(barcode);
        String badState = badState(lw);
        if (badState!=null) {
            throw new IllegalArgumentException(String.format("Labware %s cannot be stored because it is %s.",
                    lw.getBarcode(), badState));
        }
    }

    /**
     * Checks if the labware can be stored.
     * @param barcodes the barcodes being stored
     * @exception EntityNotFoundException any of the barcodes are not found
     * @exception IllegalArgumentException barcodes are repeated or any of the specified labware cannot be stored
     */
    public void validateLabwareBarcodesForStorage(Collection<String> barcodes) {
        Set<String> bcSet = new HashSet<>(barcodes.size());
        for (String bc : barcodes) {
            String bcu = bc.toUpperCase();
            if (!bcSet.add(bcu)) {
                throw new IllegalArgumentException("Repeated barcode given: "+bc);
            }
        }
        UCMap<Labware> lwMap = UCMap.from(labwareRepo.findByBarcodeIn(barcodes), Labware::getBarcode);
        List<String> missingBarcodes = barcodes.stream()
                .filter(bc -> lwMap.get(bc)==null)
                .toList();
        if (!missingBarcodes.isEmpty()) {
            throw new EntityNotFoundException("Unknown labware barcodes: "+missingBarcodes);
        }
        Set<String> badStates = new LinkedHashSet<>();
        List<String> badBarcodes = new ArrayList<>();
        for (String bc : barcodes) {
            Labware lw = lwMap.get(bc);
            String state = badState(lw);
            if (state!=null) {
                badStates.add(state);
                badBarcodes.add(lw.getBarcode());
            }
        }
        if (!badBarcodes.isEmpty()) {
            throw new IllegalArgumentException(String.format("Labware %s cannot be stored because it is %s.",
                    badBarcodes, badStates));
        }
    }

    /**
     * Gets a word for the state of a labware if it cannot be stored.
     * @param lw an item of labware
     * @return a string if the labware cannot be stored; null if it can
     */
    private static String badState(Labware lw) {
        if (lw.isDestroyed()) return "destroyed";
        if (lw.isReleased()) return "released";
        if (lw.isDiscarded()) return "discarded";
        if (lw.isEmpty()) return "empty";
        return null;
        // In CGAP storing discarded labware automatically reactivates it.
        // The behaviour of stan wrt discarded labware is not yet established.
    }

    /**
     * Sends a query through the storelight client.
     * The query is loaded from a graphql file using the given operation name.
     * @param user the user responsible (if any)
     * @param operationName the name of the operation
     * @param replaceFrom an array of strings in the query that must be replaced
     * @param replaceToObj an array of replacements
     * @param resultType the expected type of object to be returned
     * @param <T> the type of object to be returned
     * @return the object from the graphql response
     * @exception UncheckedIOException if there was an IO problem
     */
    private <T> T send(User user, String operationName, String[] replaceFrom, Object[] replaceToObj,
                       Class<T> resultType) throws UncheckedIOException {
        return objectMapper.convertValue(send(user, operationName, replaceFrom, replaceToObj), resultType);
    }

    /**
     * Sends a query through the storelight client.
     * The query is loaded from a graphql file using the given operation name.
     * @param user the user responsible (if any)
     * @param operationName the name of the operation
     * @param replaceFrom an array of strings in the query that must be replaced
     * @param replaceToObj an array of replacements
     * @param resultType the expected type of object to be returned
     * @param <T> the type of object to be returned
     * @return the object from the graphql response
     * @exception UncheckedIOException if there was an IO problem
     */
    private <T> T send(User user, String operationName, String[] replaceFrom, Object[] replaceToObj,
                       TypeReference<T> resultType) throws UncheckedIOException {
        return objectMapper.convertValue(send(user, operationName, replaceFrom, replaceToObj), resultType);
    }

    private JsonNode send(User user, String operationName, String[] replaceFrom, Object[] replaceToObj)
            throws UncheckedIOException {
        try {
            String query = readResource(operationName);
            String[] replaceTo = new String[replaceToObj.length];
            for (int i = 0; i < replaceTo.length; ++i) {
                Object r = replaceToObj[i];
                if (r instanceof Address) {
                    replaceTo[i] = objectMapper.writeValueAsString(r.toString());
                } else {
                    replaceTo[i] = objectMapper.writeValueAsString(r);
                }
            }
            if (replaceFrom.length == 1) {
                query = query.replace(replaceFrom[0], replaceTo[0]);
            } else {
                query = StringUtils.replaceEach(query, replaceFrom, replaceTo);
            }
            GraphQLResponse response = storelightClient.postQuery(query, (user == null ? null : user.getUsername()));
            checkErrors(response);
            return response.getData().get(operationName);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Checks the response for errors
     * @param response the response
     * @return the given response
     * @exception StoreException if there is an error in the response
     */
    protected GraphQLResponse checkErrors(GraphQLResponse response) throws StoreException {
        if (response.hasErrors()) {
            throw new StoreException(response.getErrors());
        }
        return response;
    }

    /**
     * Reads a graphql resource file
     * @param path the name of the file to load, without the .graphql extension
     * @return the content of the file
     * @exception IOException the resource could not be loaded
     */
    protected String readResource(String path) throws IOException {
        URL url = Resources.getResource("storelight/"+path+".graphql");
        return Resources.toString(url, Charsets.UTF_8);
    }

    /**
     * Gets Labware objects for the labware stored (directly) in a particular location.
     * Any stored barcodes that do not correspond to labware will be omitted.
     * @param locationBarcode the barcode of the location
     * @return the labware in the location
     */
    public List<Labware> getLabwareInLocation(String locationBarcode) {
        Location location = getLocation(locationBarcode);
        List<StoredItem> storedItems = location.getStored();
        if (storedItems==null || storedItems.isEmpty()) {
            return List.of();
        }
        List<String> labwareBarcodes = storedItems.stream().map(StoredItem::getBarcode).collect(toList());
        return labwareRepo.findByBarcodeIn(labwareBarcodes);
    }

    /**
     * Transfer stored labware between specified locations.
     * @param user the user responsible for the request
     * @param sourceBarcode the barcode of the source location
     * @param destinationBarcode the barcode of the destination location
     * @return the updated destination location
     */
    public Location transfer(User user, String sourceBarcode, String destinationBarcode) {
        if (sourceBarcode.equalsIgnoreCase(destinationBarcode)) {
            throw new IllegalArgumentException("Source and destination cannot be the same location.");
        }
        Location source = getLocation(sourceBarcode);
        if (source.getStored().isEmpty()) {
            throw new IllegalArgumentException("Location "+source.getBarcode()+" is empty.");
        }
        List<String> listedBarcodes = source.getStored().stream().map(StoredItem::getBarcode).collect(toList());
        Set<String> stanBarcodes = labwareRepo.findBarcodesByBarcodeIn(listedBarcodes).stream()
                .map(String::toUpperCase)
                .collect(toSet());
        if (stanBarcodes.isEmpty()) {
            throw new IllegalArgumentException("None of the labware stored in that location belongs to Stan.");
        }

        List<StoreInput> storeInputs = source.getStored().stream()
                .filter(item -> stanBarcodes.contains(item.getBarcode().toUpperCase()))
                .map(item -> new StoreInput(item.getBarcode(), item.getAddress()))
                .collect(toList());
        return store(user, storeInputs, destinationBarcode);
    }
}
