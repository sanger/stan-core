package uk.ac.sanger.sccp.stan.service.releasefile;

import uk.ac.sanger.sccp.stan.GraphQLCustomTypes;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.utils.tsv.TsvColumn;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;

public enum ReleaseColumn implements TsvColumn<ReleaseEntry> {
    Released_from_box_location(ReleaseEntry::getStorageAddress),
    Released_labware_barcode(Compose.labware, Labware::getBarcode),
    Biological_state(Compose.sample, Sample::getBioState),
    Labware_type(Compose.labware, Labware::getLabwareType, LabwareType::getName),
    Slot_of_labware(Compose.slot, Slot::getAddress),
    External_identifier(Compose.tissue, Tissue::getExternalName),
    Donor_name(Compose.donor, Donor::getDonorName),
    Life_stage(Compose.donor, Donor::getLifeStage),
    Tissue_type_code(Compose.tissue, Tissue::getTissueType, TissueType::getCode),
    Spatial_location(Compose.tissue, Tissue::getSpatialLocation, SpatialLocation::getCode),
    Replicate_number(Compose.tissue, Tissue::getReplicate),
    Section_number(Compose.sample, Sample::getSection),
    Section_position_in_slot(ReleaseEntry::getSamplePosition),
    Section_thickness(ReleaseEntry::getSectionThickness),
    Date_sectioned(ReleaseEntry::getSectionDate),
    Section_comment(ReleaseEntry::getSectionComment),
    Last_section_number(ReleaseEntry::getLastSection, ReleaseFileMode.NORMAL),
    Fixative(Compose.tissue, Tissue::getFixative, HasName::getName),
    Solution_currently_in(ReleaseEntry::getSolution),
    Embedding_medium(Compose.tissue, Tissue::getMedium, Medium::getName),
    Source_barcode(ReleaseEntry::getSourceBarcode, ReleaseFileMode.CDNA),
    Source_address(ReleaseEntry::getSourceAddress, ReleaseFileMode.CDNA),
    Stain_type(ReleaseEntry::getStainType),
    Stain_QC_comment(ReleaseEntry::getStainQcComment),
    Bond_barcode(ReleaseEntry::getBondBarcode),
    RNAscope_plex(ReleaseEntry::getRnascopePlex),
    IHC_plex(ReleaseEntry::getIhcPlex),
    Tissue_coverage(ReleaseEntry::getCoverage),
    Permeabilisation_time(ReleaseEntry::getPermTime),
    Cq_value(ReleaseEntry::getCq),
    Number_of_amplification_cycles(ReleaseEntry::getAmplificationCycles),
    Visium_concentration(ReleaseEntry::getVisiumConcentration),
    Visium_concentration_type(ReleaseEntry::getVisiumConcentrationType),
    Dual_index_plate_type(ReleaseEntry::getReagentPlateType),
    Dual_index_plate_name(ReleaseEntry::getReagentSource),
    // tag columns go here
    Probe_hybridisation_start(ReleaseEntry::getHybridStart, Compose.formatTime),
    Xenium_plex_number(ReleaseEntry::getXeniumPlex),
    Xenium_probe_panel(ReleaseEntry::getXeniumProbe),
    Xenium_probe_lot(ReleaseEntry::getXeniumProbeLot),
    Probe_hybridisation_end(ReleaseEntry::getHybridEnd, Compose.formatTime),
    Probe_comments(ReleaseEntry::getHybridComment),
    Xenium_decoding_reagent_lot(ReleaseEntry::getXeniumReagentLot),
    Xenium_run_name(ReleaseEntry::getXeniumRun),
    Xenium_cassette_position(ReleaseEntry::getXeniumCassettePosition),
    Xenium_ROI(ReleaseEntry::getXeniumRoi),
    Xenium_start(ReleaseEntry::getXeniumStart, Compose.formatTime),
    Xenium_completion(ReleaseEntry::getXeniumEnd, Compose.formatTime),
    Xenium_comments(ReleaseEntry::getXeniumComment),
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
        if (this == Visium_concentration) {
            return "Visium concentration (pg/uL)";
        }
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
        private static final Function<LocalDateTime, String> formatTime = t -> t.format(GraphQLCustomTypes.DATE_TIME_FORMAT);

        private static <A, B> Function<A, B> skipNull(Function<A, B> func) {
            return a -> (a==null ? null : func.apply(a));
        }
    }
}
