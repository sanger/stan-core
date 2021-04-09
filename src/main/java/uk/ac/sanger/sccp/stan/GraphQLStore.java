package uk.ac.sanger.sccp.stan;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.model.store.*;
import uk.ac.sanger.sccp.stan.repo.UserRepo;
import uk.ac.sanger.sccp.stan.service.store.StoreService;

import java.util.List;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * GraphQL operations for storage
 * @author dr6
 */
@Component
public class GraphQLStore extends BaseGraphQLResource {
    Logger log = LoggerFactory.getLogger(GraphQLStore.class);

    final StoreService storeService;

    public GraphQLStore(ObjectMapper objectMapper, AuthenticationComponent authComp, UserRepo userRepo,
                        StoreService storeService) {
        super(objectMapper, authComp, userRepo);
        this.storeService = storeService;
    }

    private void logRequest(String name, User user, Object request) {
        if (log.isInfoEnabled()) {
            if (request instanceof String) {
                request = repr(request);
            }
            log.info("{} requested by {}: {}", name, (user==null ? null : repr(user.getUsername())), request);
        }
    }

    public DataFetcher<StoredItem> storeBarcode() {
        return dfe -> {
            User user = checkUser(User.Role.normal);
            String itemBarcode = dfe.getArgument("barcode");
            String locationBarcode = dfe.getArgument("locationBarcode");
            Address address = dfe.getArgument("address");
            if (log.isInfoEnabled()) {
                log.info("Store barcode request from {}: barcode: {}, locationBarcode: {}, address: {}",
                        user.getUsername(), repr(itemBarcode), repr(locationBarcode), address);
            }
            return storeService.storeBarcode(user, itemBarcode, locationBarcode, address);
        };
    }

    public DataFetcher<UnstoredItem> unstoreBarcode() {
        return dfe -> {
            User user = checkUser(User.Role.normal);
            String itemBarcode = dfe.getArgument("barcode");
            logRequest("UnstoreBarcode", user, itemBarcode);
            return storeService.unstoreBarcode(user, itemBarcode);
        };
    }

    public DataFetcher<UnstoreResult> empty() {
        return dfe -> {
            User user = checkUser(User.Role.normal);
            String locationBarcode = dfe.getArgument("locationBarcode");
            logRequest("Empty", user, locationBarcode);
            return storeService.empty(user, locationBarcode);
        };
    }

    public DataFetcher<Location> setLocationCustomName() {
        return dfe -> {
            User user = checkUser(User.Role.admin);
            String locationBarcode = dfe.getArgument("locationBarcode");
            String customName = dfe.getArgument("customName");
            if (log.isInfoEnabled()) {
                log.info("Set location custom name requested by {}: locationBarcode={}, customName={}",
                        repr(user.getUsername()), repr(locationBarcode), repr(customName));
            }
            return storeService.setLocationCustomName(user, locationBarcode, customName);
        };
    }

    public DataFetcher<Location> getLocation() {
        return dfe -> {
            String locationBarcode = dfe.getArgument("locationBarcode");
            return storeService.getLocation(locationBarcode);
        };
    }

    public DataFetcher<List<StoredItem>> getStored() {
        return dfe -> {
            List<String> barcodes = arg(dfe, "barcodes", new TypeReference<List<String>>() {});
            return storeService.getStored(barcodes);
        };
    }

}
