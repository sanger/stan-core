package uk.ac.sanger.sccp.stan.service.releasefile;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.utils.tsv.TsvColumn;

import java.util.function.Function;

public enum ReleaseColumn implements TsvColumn<ReleaseEntry> {
    Barcode(Compose.labware, Labware::getBarcode),
    Labware_type(Compose.labware, Labware::getLabwareType, LabwareType::getName),
    Address(Compose.slot, Slot::getAddress),
    Donor_name(Compose.donor, Donor::getDonorName),
    Life_stage(Compose.donor, Donor::getLifeStage),
    Tissue_type(Compose.tissue, Tissue::getTissueType, TissueType::getCode),
    Spatial_location(Compose.tissue, Tissue::getSpatialLocation, SpatialLocation::getCode),
    Replicate_number(Compose.tissue, Tissue::getReplicate),
    Section_number(Compose.sample, Sample::getSection),
    Last_section_number(ReleaseEntry::getLastSection),
    Source_barcode(ReleaseEntry::getOriginalBarcode),
    Section_thickness(ReleaseEntry::getSectionThickness),
    ;

    ReleaseColumn(Function<ReleaseEntry, ?> function) {
        this.function = function;
    }

    <E> ReleaseColumn(Function<ReleaseEntry, E> composition, Function<E, ?> function) {
        this(composition.andThen(Compose.skipNull(function)));
    }

    <E, F> ReleaseColumn(Function<ReleaseEntry, E> composition, Function<E, F> func1, Function<F, ?> func2) {
        this(composition.andThen(Compose.skipNull(func1)).andThen(Compose.skipNull(func2)));
    }

    private final Function<ReleaseEntry, ?> function;

    @Override
    public String get(ReleaseEntry entry) {
        Object value = function.apply(entry);
        return (value==null ? null : value.toString());
    }

    @Override
    public String toString() {
        return this.name().replace('_',' ');
    }

    private static class Compose {
        private static final Function<ReleaseEntry, Labware> labware = ReleaseEntry::getLabware;
        private static final Function<ReleaseEntry, Sample> sample = ReleaseEntry::getSample;
        private static final Function<ReleaseEntry, Slot> slot = ReleaseEntry::getSlot;
        private static final Function<ReleaseEntry, Tissue> tissue = r -> r.getSample().getTissue();
        private static final Function<ReleaseEntry, Donor> donor = r -> r.getSample().getTissue().getDonor();

        private static <A, B> Function<A, B> skipNull(Function<A, B> func) {
            return a -> (a==null ? null : func.apply(a));
        }
    }
}
