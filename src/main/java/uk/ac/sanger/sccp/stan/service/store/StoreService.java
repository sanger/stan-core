package uk.ac.sanger.sccp.stan.service.store;

import com.fasterxml.jackson.core.type.TypeReference;
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
import uk.ac.sanger.sccp.stan.service.EmailService;
import uk.ac.sanger.sccp.utils.GraphQLClient.GraphQLResponse;

import javax.persistence.EntityNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.*;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
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

    public StoredItem storeBarcode(User user, String barcode, String locationBarcode, Address address) {
        requireNonNull(user, "User is null.");
        requireNonNull(barcode, "Item barcode is null.");
        requireNonNull(locationBarcode, "Location barcode is null.");
        validateLabwareBarcodeForStorage(barcode);
        return send(user, "storeBarcode", new String[] {"\"BARCODE\"", "\"LOCATIONBARCODE\"", "\"ADDRESS\"" },
                new Object[] { barcode, locationBarcode, address}, StoredItem.class).fixInternalLinks();
    }

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
                new String[] {"\"LOCATIONBARCODE\"", "\"NAME\""},
                new Object[] { location.getBarcode(), location.getName() },
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

    private static String badState(Labware lw) {
        if (lw.isDestroyed()) return "destroyed";
        if (lw.isReleased()) return "released";
        if (lw.isDiscarded()) return "discarded";
        if (lw.isEmpty()) return "empty";
        return null;
        // In CGAP storing discarded labware automatically reactivates it.
        // The behaviour of stan wrt discarded labware is not yet established.
    }

    private <T> T send(User user, String operationName, String[] replaceFrom, Object[] replaceToObj,
                       Class<T> resultType) throws UncheckedIOException {
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
            return objectMapper.convertValue(response.getData().get(operationName), resultType);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected GraphQLResponse checkErrors(GraphQLResponse response) {
        if (response.hasErrors()) {
            throw new StoreException(response.getErrors());
        }
        return response;
    }

    @SuppressWarnings("UnstableApiUsage")
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
}
