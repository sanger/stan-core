package uk.ac.sanger.sccp.stan.service.releasefile;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.utils.tsv.TsvColumn;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;

public enum ReleaseColumn implements TsvColumn<ReleaseEntry> {
    Barcode(Compose.labware, Labware::getBarcode),
    Labware_type(Compose.labware, Labware::getLabwareType, LabwareType::getName),
    Address(Compose.slot, Slot::getAddress),
    Donor_name(Compose.donor, Donor::getDonorName),
    Life_stage(Compose.donor, Donor::getLifeStage),
    External_identifier(Compose.tissue, Tissue::getExternalName),
    Tissue_type(Compose.tissue, Tissue::getTissueType, TissueType::getCode),
    Spatial_location(Compose.tissue, Tissue::getSpatialLocation, SpatialLocation::getCode),
    Replicate_number(Compose.tissue, Tissue::getReplicate),
    Section_number(Compose.sample, Sample::getSection),
    Last_section_number(ReleaseEntry::getLastSection, ReleaseFileMode.NORMAL),
    Source_barcode(ReleaseEntry::getSourceBarcode),
    Source_address(ReleaseEntry::getSourceAddress, ReleaseFileMode.CDNA),
    Section_thickness(ReleaseEntry::getSectionThickness),
    ;

    private final Function<ReleaseEntry, ?> function;
    private final ReleaseFileMode mode;

    ReleaseColumn(Function<ReleaseEntry, ?> function, ReleaseFileMode mode) {
        this.function = function;
        this.mode = mode;
    }

    ReleaseColumn(Function<ReleaseEntry, ?> function) {
        this(function, (ReleaseFileMode) null);
    }


    <E> ReleaseColumn(Function<ReleaseEntry, E> composition, Function<E, ?> function) {
        this(composition.andThen(Compose.skipNull(function)));
    }

    <E, F> ReleaseColumn(Function<ReleaseEntry, E> composition, Function<E, F> func1, Function<F, ?> func2) {
        this(composition.andThen(Compose.skipNull(func1)).andThen(Compose.skipNull(func2)));
    }

    public ReleaseFileMode getMode() {
        return this.mode;
    }

    @Override
    public String get(ReleaseEntry entry) {
        Object value = function.apply(entry);
        return (value==null ? null : value.toString());
    }

    @Override
    public String toString() {
        return this.name().replace('_',' ');
    }

    public static List<ReleaseColumn> forMode(ReleaseFileMode mode) {
        return Arrays.stream(values()).filter(rc -> rc.mode==null || rc.mode==mode).collect(toList());
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
