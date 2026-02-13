package uk.ac.sanger.sccp.stan.service.register;

import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.request.register.*;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Utility for checking that the description of tissue in a {@link BlockRegisterRequest}
 * matches the information in an existing tissue.
 * @author dr6
 */
@Service
public class BlockFieldChecker {
    /** Chains two functions, but skips the second if the first returns null */
    private static <A, B, C> Function<A, C> chain(Function<A, B> ab, Function<? super B, ? extends C> bc) {
        return ab.andThen(b -> b==null ? null : bc.apply(b));
    }
    enum Field {
        DONOR(null, BlockRegisterSample::getDonorIdentifier, Tissue::getDonor, Donor::getDonorName, "donor identifier"),
        HMDMC(null, BlockRegisterSample::getHmdmc, Tissue::getHmdmc, Hmdmc::getHmdmc, "HuMFre number"),
        TTYPE(null, BlockRegisterSample::getTissueType, Tissue::getTissueType, TissueType::getName, "tissue type"),
        SL(null, BlockRegisterSample::getSpatialLocation, Tissue::getSpatialLocation, SpatialLocation::getCode, "spatial location"),
        REPLICATE(null, BlockRegisterSample::getReplicateNumber, Tissue::getReplicate, null, "replicate number"),
        MEDIUM(BlockRegisterLabware::getMedium, null, Tissue::getMedium, Medium::getName, "medium"),
        FIXATIVE(BlockRegisterLabware::getFixative, null, Tissue::getFixative, Fixative::getName, "fixative"),
        COLLECTION_DATE(null, BlockRegisterSample::getSampleCollectionDate, Tissue::getCollectionDate, null, "sample collection date", true),
        CELL_CLASS(null, BlockRegisterSample::getCellClass, Tissue::getCellClass, CellClass::getName, "cellular classification"),
        ;

        private final Function<Tissue, ?> tissueFunction;
        private final Function<BlockRegisterSample, ?> brsFunction;
        private final Function<BlockRegisterLabware, ?> brlFunction;
        private final String description;
        private final boolean replaceMissing;

        <S> Field(Function<BlockRegisterLabware, ?> brlFunction, Function<BlockRegisterSample, ?> brsFunction, Function<Tissue, S> tissueFunction, Function<S,?> subFunction, String description, boolean replaceMissing) {
            this.brlFunction = brlFunction;
            this.brsFunction = brsFunction;
            this.tissueFunction = subFunction==null ? tissueFunction : chain(tissueFunction, subFunction);
            this.description = description;
            this.replaceMissing = replaceMissing;
        }

        <S> Field(Function<BlockRegisterLabware, ?> brlFunction, Function<BlockRegisterSample, ?> brsFunction, Function<Tissue, S> tissueFunction, Function<S,?> subFunction, String description) {
            this(brlFunction, brsFunction, tissueFunction, subFunction, description, false);
        }

        public Object apply(BlockRegisterLabware brl, BlockRegisterSample brs) {
            return brlFunction!=null ? brlFunction.apply(brl) : brsFunction.apply(brs);
        }
        public Object apply(Tissue tissue) {
            return tissueFunction.apply(tissue);
        }
    }

    public void check(Consumer<String> problemConsumer, BlockRegisterLabware brl, BlockRegisterSample brs, Tissue tissue) {
        for (Field field : Field.values()) {
            Object oldValue = field.apply(tissue);
            if (field.replaceMissing && oldValue==null) {
                continue;
            }
            Object newValue = field.apply(brl, brs);
            if (!match(oldValue, newValue)) {
                problemConsumer.accept(String.format("Expected %s to be %s for existing tissue %s.",
                        field.description, oldValue, tissue.getExternalName()));
            }
        }
    }

    public static boolean match(Object oldValue, Object newValue) {
        if (oldValue==null) {
            return (newValue==null || newValue.equals(""));
        }
        if (oldValue instanceof String && newValue instanceof String) {
            return ((String) oldValue).equalsIgnoreCase((String) newValue);
        }
        return oldValue.equals(newValue);
    }
}
