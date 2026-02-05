package uk.ac.sanger.sccp.stan.service.register;

import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.request.register.BlockRegisterRequest_old;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Utility for checking that the description of tissue in a {@link BlockRegisterRequest_old}
 * matches the information in an existing tissue.
 * @author dr6
 */
@Service
public class TissueFieldChecker {
    /** Chains two functions, but skips the second if the first returns null */
    private static <A, B, C> Function<A, C> chain(Function<A, B> ab, Function<? super B, ? extends C> bc) {
        return ab.andThen(b -> b==null ? null : bc.apply(b));
    }
    enum Field {
        DONOR(Tissue::getDonor, Donor::getDonorName, BlockRegisterRequest_old::getDonorIdentifier, "donor identifier"),
        HMDMC(Tissue::getHmdmc, Hmdmc::getHmdmc, BlockRegisterRequest_old::getHmdmc, "HuMFre number"),
        TTYPE(Tissue::getTissueType, TissueType::getName, BlockRegisterRequest_old::getTissueType, "tissue type"),
        SL(Tissue::getSpatialLocation, SpatialLocation::getCode, BlockRegisterRequest_old::getSpatialLocation, "spatial location"),
        REPLICATE(Tissue::getReplicate, BlockRegisterRequest_old::getReplicateNumber, "replicate number", false),
        MEDIUM(Tissue::getMedium, Medium::getName, BlockRegisterRequest_old::getMedium, "medium"),
        FIXATIVE(Tissue::getFixative, Fixative::getName, BlockRegisterRequest_old::getFixative, "fixative"),
        COLLECTION_DATE(Tissue::getCollectionDate, BlockRegisterRequest_old::getSampleCollectionDate, "sample collection date", true),
        CELL_CLASS(Tissue::getCellClass, CellClass::getName, BlockRegisterRequest_old::getCellClass, "cellular classification"),
        ;

        private final Function<Tissue, ?> tissueFunction;
        private final Function<BlockRegisterRequest_old, ?> brFunction;
        private final String description;
        private final boolean replaceMissing;

        Field(Function<Tissue, ?> tissueFunction, Function<BlockRegisterRequest_old, ?> brFunction,
              String description, boolean replaceMissing) {
            this.tissueFunction = tissueFunction;
            this.brFunction = brFunction;
            this.description = description;
            this.replaceMissing = replaceMissing;
        }

        <X> Field(Function<Tissue, X> tissueFunction, Function<? super X, ?> xFunction, Function<BlockRegisterRequest_old, ?> brFunction,
                  String description) {
            this(chain(tissueFunction, xFunction), brFunction, description, false);
        }

        public Object apply(Tissue tissue) {
            return tissueFunction.apply(tissue);
        }
        public Object apply(BlockRegisterRequest_old br) {
            return brFunction.apply(br);
        }
    }

    public void check(Consumer<String> problemConsumer, BlockRegisterRequest_old br, Tissue tissue) {
        for (Field field : Field.values()) {
            Object oldValue = field.apply(tissue);
            if (field.replaceMissing && oldValue==null) {
                continue;
            }
            Object newValue = field.apply(br);
            if (!match(oldValue, newValue)) {
                problemConsumer.accept(String.format("Expected %s to be %s for existing tissue %s.",
                        field.description, oldValue, tissue.getExternalName()));
            }
        }
    }

    public boolean match(Object oldValue, Object newValue) {
        if (oldValue==null) {
            return (newValue==null || newValue.equals(""));
        }
        if (oldValue instanceof String && newValue instanceof String) {
            return ((String) oldValue).equalsIgnoreCase((String) newValue);
        }
        return oldValue.equals(newValue);
    }
}
