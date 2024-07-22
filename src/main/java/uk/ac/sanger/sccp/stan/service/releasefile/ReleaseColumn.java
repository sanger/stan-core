package uk.ac.sanger.sccp.stan.service.releasefile;

import org.jetbrains.annotations.NotNull;
import uk.ac.sanger.sccp.stan.GraphQLCustomTypes;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.utils.tsv.TsvColumn;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;

public enum ReleaseColumn implements TsvColumn<ReleaseEntry> {
    Released_from_box_name(ReleaseEntry::getLocationName),
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
    Flag_description(ReleaseEntry::getFlagDescription),
    Section_position_in_slot(ReleaseEntry::getSamplePosition, ReleaseFileOption.Histology, ReleaseFileOption.RNAscope_IHC, ReleaseFileOption.Visium, ReleaseFileOption.Xenium),
    Section_thickness(ReleaseEntry::getSectionThickness, ReleaseFileOption.Histology, ReleaseFileOption.RNAscope_IHC, ReleaseFileOption.Visium, ReleaseFileOption.Xenium),
    Date_sectioned(ReleaseEntry::getSectionDate, ReleaseFileOption.Histology, ReleaseFileOption.RNAscope_IHC, ReleaseFileOption.Visium, ReleaseFileOption.Xenium),
    Section_comment(ReleaseEntry::getSectionComment, ReleaseFileOption.Histology, ReleaseFileOption.RNAscope_IHC, ReleaseFileOption.Visium, ReleaseFileOption.Xenium),
    Last_section_number(ReleaseEntry::getLastSection, ReleaseFileMode.NORMAL, ReleaseFileOption.Sample_processing, ReleaseFileOption.Histology),
    Fixative(Compose.tissue, Tissue::getFixative, HasName::getName, ReleaseFileOption.Sample_processing),
    Solution_currently_in(ReleaseEntry::getSolution, ReleaseFileOption.Sample_processing),
    Embedding_medium(Compose.tissue, Tissue::getMedium, Medium::getName, ReleaseFileOption.Sample_processing),
    Source_barcode(ReleaseEntry::getSourceBarcode, ReleaseFileMode.CDNA, ReleaseFileOption.Visium),
    Source_address(ReleaseEntry::getSourceAddress, ReleaseFileMode.CDNA, ReleaseFileOption.Visium),
    Stain_type(ReleaseEntry::getStainType, ReleaseFileOption.Histology, ReleaseFileOption.RNAscope_IHC, ReleaseFileOption.Visium, ReleaseFileOption.Xenium),
    Stain_QC_comment(ReleaseEntry::getStainQcComment, ReleaseFileOption.Histology, ReleaseFileOption.RNAscope_IHC, ReleaseFileOption.Visium, ReleaseFileOption.Xenium),
    Bond_barcode(ReleaseEntry::getBondBarcode, ReleaseFileOption.RNAscope_IHC),
    RNAscope_plex(ReleaseEntry::getRnascopePlex, ReleaseFileOption.RNAscope_IHC),
    IHC_plex(ReleaseEntry::getIhcPlex, ReleaseFileOption.RNAscope_IHC),
    Visium_barcode(ReleaseEntry::getVisiumBarcode, ReleaseFileOption.Visium),
    Tissue_coverage(ReleaseEntry::getCoverage, ReleaseFileOption.Visium),
    Permeabilisation_time(ReleaseEntry::getPermTime, ReleaseFileOption.Visium),
    Cq_value(ReleaseEntry::getCq, ReleaseFileOption.Visium),
    Number_of_amplification_cycles(ReleaseEntry::getAmplificationCycles, ReleaseFileOption.Visium),
    Visium_concentration(ReleaseEntry::getVisiumConcentration, ReleaseFileOption.Visium),
    Visium_concentration_type(ReleaseEntry::getVisiumConcentrationType, ReleaseFileOption.Visium),
    Dual_index_plate_type(ReleaseEntry::getReagentPlateType, ReleaseFileOption.Visium),
    Dual_index_plate_name(ReleaseEntry::getReagentSource, ReleaseFileOption.Visium),
    // tag columns go here for visium
    Probe_hybridisation_start(ReleaseEntry::getHybridStart, Compose.formatTime, ReleaseFileOption.Xenium),
    Xenium_plex_number(ReleaseEntry::getXeniumPlex, ReleaseFileOption.Xenium),
    Xenium_probe_panel(ReleaseEntry::getXeniumProbe, ReleaseFileOption.Xenium),
    Xenium_probe_lot(ReleaseEntry::getXeniumProbeLot, ReleaseFileOption.Xenium),
    Probe_hybridisation_end(ReleaseEntry::getHybridEnd, Compose.formatTime, ReleaseFileOption.Xenium),
    Probe_comments(ReleaseEntry::getHybridComment, ReleaseFileOption.Xenium, ReleaseFileOption.Xenium),
    Xenium_decoding_reagent_A_lot(ReleaseEntry::getXeniumReagentALot, ReleaseFileOption.Xenium),
    Xenium_decoding_reagent_B_lot(ReleaseEntry::getXeniumReagentBLot, ReleaseFileOption.Xenium),
    Xenium_run_name(ReleaseEntry::getXeniumRun, ReleaseFileOption.Xenium),
    Xenium_cassette_position(ReleaseEntry::getXeniumCassettePosition, ReleaseFileOption.Xenium),
    Xenium_ROI(ReleaseEntry::getXeniumRoi, ReleaseFileOption.Xenium),
    Xenium_start(ReleaseEntry::getXeniumStart, Compose.formatTime, ReleaseFileOption.Xenium),
    Xenium_completion(ReleaseEntry::getXeniumEnd, Compose.formatTime, ReleaseFileOption.Xenium),
    Xenium_comments(ReleaseEntry::getXeniumComment, ReleaseFileOption.Xenium),
    ;

    private final Function<ReleaseEntry, ?> function;
    private final ReleaseFileMode mode;
    private final Set<ReleaseFileOption> options;

    ReleaseColumn(Function<ReleaseEntry, ?> function, ReleaseFileMode mode, ReleaseFileOption... options) {
        this.function = function;
        this.mode = mode;
        if (options.length==0) {
            this.options = null;
        } else {
            this.options = EnumSet.copyOf(Arrays.asList(options));
        }
    }

    ReleaseColumn(Function<ReleaseEntry, ?> function) {
        this(function, (ReleaseFileMode) null);
    }

    ReleaseColumn(Function<ReleaseEntry, ?> function, ReleaseFileOption... options) {
        this(function, (ReleaseFileMode) null, options);
    }

    <E> ReleaseColumn(Function<ReleaseEntry, E> composition, Function<E, ?> function, ReleaseFileOption... options) {
        this(composition.andThen(Compose.skipNull(function)), options);
    }

    <E,F> ReleaseColumn(Function<ReleaseEntry, E> composition, Function<E, F> func1, Function<F, ?> func2, ReleaseFileOption... options) {
        this(composition.andThen(Compose.skipNull(func1)).andThen(Compose.skipNull(func2)), options);
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

    public static List<ReleaseColumn> forModesAndOptions(@NotNull Collection<ReleaseFileMode> modes, @NotNull Collection<ReleaseFileOption> options) {
        return Arrays.stream(values()).filter(rc -> rc.include(modes, options)).collect(toList());
    }

    public boolean modeFilter(Collection<ReleaseFileMode> modes) {
        return (this.mode==null || modes.contains(this.mode));
    }

    public boolean optionFilter(@NotNull Collection<ReleaseFileOption> selectedOptions) {
        return (this.options==null || selectedOptions.stream().anyMatch(this.options::contains));
    }

    public boolean include(Collection<ReleaseFileMode> modes, @NotNull Collection<ReleaseFileOption> selectedOptions) {
        return this.modeFilter(modes) && this.optionFilter(selectedOptions);
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
