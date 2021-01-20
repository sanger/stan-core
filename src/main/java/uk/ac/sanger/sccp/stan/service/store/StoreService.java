package uk.ac.sanger.sccp.stan.service.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.model.store.*;
import uk.ac.sanger.sccp.utils.GraphQLClient.GraphQLResponse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Collection;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Service for dealing with storage of labware
 * @author dr6
 */
@Service
public class StoreService {
    private final StorelightClient storelightClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public StoreService(StorelightClient storelightClient) {
        this.storelightClient = storelightClient;
        this.objectMapper = new ObjectMapper();
    }

    public StoredItem storeBarcode(User user, String barcode, String locationBarcode, Address address) {
        requireNonNull(user, "User is null.");
        requireNonNull(barcode, "Item barcode is null.");
        requireNonNull(locationBarcode, "Location barcode is null.");
        return send(user, "storeBarcode", new String[] {"\"BARCODE\"", "\"LOCATIONBARCODE\"", "\"ADDRESS\"" },
                new Object[] { barcode, locationBarcode, address}, StoredItem.class).fixInternalLinks();
    }

    public UnstoredItem unstoreBarcode(User user, String barcode) {
        requireNonNull(user, "User is null.");
        requireNonNull(barcode, "Barcode is null.");
        return send(user, "unstoreBarcode", new String[] { "\"BARCODE\"" }, new Object[] { barcode},
                    UnstoredItem.class);
    }

    public UnstoreResult empty(User user, String locationBarcode) {
        requireNonNull(user, "User is null.");
        requireNonNull(locationBarcode, "Location barcode is null.");
        return send(user, "empty", new String[] { "\"LOCATIONBARCODE\"" },
                    new String[] { locationBarcode }, UnstoreResult.class);
    }

    public Location setLocationCustomName(User user, String locationBarcode, String customName) {
        requireNonNull(user, "User is null.");
        requireNonNull(locationBarcode, "Location barcode is null.");
        Location location = getLocation(locationBarcode);
        location.setCustomName(customName);
        return send(user, "editLocation",
                new String[] {"\"LOCATIONBARCODE\"", "\"DESCRIPTION\""},
                new Object[] { location.getBarcode(), location.getDescription() },
                Location.class).fixInternalLinks();
    }

    public Location getLocation(String locationBarcode) {
        requireNonNull(locationBarcode, "Location barcode is null.");
        return send(null, "location", new String[] { "\"LOCATIONBARCODE\""},
                new Object[] { locationBarcode }, Location.class).fixInternalLinks();
    }

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
                    new TypeReference<List<StoredItem>>() {});
            items.forEach(StoredItem::fixInternalLinks);
            return items;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private <T> T send(User user, String operationName, String[] replaceFrom, Object[] replaceToObj,
                       Class<T> resultType) throws UncheckedIOException {
        try {
            String query = readResource(operationName);
            String[] replaceTo = new String[replaceToObj.length];
            for (int i = 0; i < replaceTo.length; ++i) {
                Object r = replaceToObj[i];
                if (r==null) {
                    replaceTo[i] = "null";
                } else if (r instanceof Address) {
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
            return objectMapper.convertValue(response.getData().get(operationName), resultType);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private GraphQLResponse checkErrors(GraphQLResponse response) {
        if (response.hasErrors()) {
            throw new StoreException(response.getErrors());
        }
        return response;
    }

    @SuppressWarnings("UnstableApiUsage")
    private String readResource(String path) throws IOException {
        URL url = Resources.getResource("storelight/"+path+".graphql");
        return Resources.toString(url, Charsets.UTF_8);
    }
}
