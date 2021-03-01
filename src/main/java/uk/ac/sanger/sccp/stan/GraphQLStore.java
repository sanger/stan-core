package uk.ac.sanger.sccp.stan;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetcher;
import org.springframework.stereotype.Component;
import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.model.User;
import uk.ac.sanger.sccp.stan.model.store.*;
import uk.ac.sanger.sccp.stan.repo.UserRepo;
import uk.ac.sanger.sccp.stan.service.store.StoreService;

import java.util.List;

/**
 * GraphQL operations for storage
 * @author dr6
 */
@Component
public class GraphQLStore extends BaseGraphQLResource {
    final StoreService storeService;

    public GraphQLStore(ObjectMapper objectMapper, AuthenticationComponent authComp, UserRepo userRepo,
                        StoreService storeService) {
        super(objectMapper, authComp, userRepo);
        this.storeService = storeService;
    }

    public DataFetcher<StoredItem> storeBarcode() {
        return dfe -> {
            User user = checkUser();
            String itemBarcode = dfe.getArgument("barcode");
            String locationBarcode = dfe.getArgument("locationBarcode");
            Address address = dfe.getArgument("address");
            return storeService.storeBarcode(user, itemBarcode, locationBarcode, address);
        };
    }

    public DataFetcher<UnstoredItem> unstoreBarcode() {
        return dfe -> {
            User user = checkUser();
            String itemBarcode = dfe.getArgument("barcode");
            return storeService.unstoreBarcode(user, itemBarcode);
        };
    }

    public DataFetcher<UnstoreResult> empty() {
        return dfe -> {
            User user = checkUser();
            String locationBarcode = dfe.getArgument("locationBarcode");
            return storeService.empty(user, locationBarcode);
        };
    }

    public DataFetcher<Location> setLocationCustomName() {
        return dfe -> {
            User user = checkUser();
            String locationBarcode = dfe.getArgument("locationBarcode");
            String customName = dfe.getArgument("customName");
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
