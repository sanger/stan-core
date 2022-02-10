package uk.ac.sanger.sccp.stan.service.register;

import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.request.register.BlockRegisterRequest;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Utility for checking that the description of tissue in a {@link BlockRegisterRequest}
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
        DONOR(Tissue::getDonor, Donor::getDonorName, BlockRegisterRequest::getDonorIdentifier, "donor identifier"),
        HMDMC(Tissue::getHmdmc, Hmdmc::getHmdmc, BlockRegisterRequest::getHmdmc, "HuMFre number"),
        TTYPE(Tissue::getTissueType, TissueType::getName, BlockRegisterRequest::getTissueType, "tissue type"),
        SL(Tissue::getSpatialLocation, SpatialLocation::getCode, BlockRegisterRequest::getSpatialLocation, "spatial location"),
        REPLICATE(Tissue::getReplicate, BlockRegisterRequest::getReplicateNumber, "replicate number"),
        MEDIUM(Tissue::getMedium, Medium::getName, BlockRegisterRequest::getMedium, "medium"),
        MOULDSIZE(Tissue::getMouldSize, MouldSize::getName, BlockRegisterRequest::getMouldSize, "mould size"),
        FIXATIVE(Tissue::getFixative, Fixative::getName, BlockRegisterRequest::getFixative, "fixative"),
        ;

        private final Function<Tissue, ?> tissueFunction;
        private final Function<BlockRegisterRequest, ?> brFunction;
        private final String description;

        Field(Function<Tissue, ?> tissueFunction, Function<BlockRegisterRequest, ?> brFunction,
              String description) {
            this.tissueFunction = tissueFunction;
            this.brFunction = brFunction;
            this.description = description;
        }

        <X> Field(Function<Tissue, X> tissueFunction, Function<? super X, ?> xFunction, Function<BlockRegisterRequest, ?> brFunction,
                  String description) {
            this(chain(tissueFunction, xFunction), brFunction, description);
        }

        public Object apply(Tissue tissue) {
            return tissueFunction.apply(tissue);
        }
        public Object apply(BlockRegisterRequest br) {
            return brFunction.apply(br);
        }
    }

    public void check(Consumer<String> problemConsumer, BlockRegisterRequest br, Tissue tissue) {
        for (Field field : Field.values()) {
            Object oldValue = field.apply(tissue);
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
