package uk.ac.sanger.sccp.stan.service.history;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentAction;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentPlate;
import uk.ac.sanger.sccp.stan.model.taglayout.TagLayout;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.*;
import java.util.function.Function;

import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullToEmpty;
import static uk.ac.sanger.sccp.utils.BasicUtils.stream;

/**
 * Service to describe the details of reagent transfers for particular operations.
 * @author dr6
 */
@Service
public class ReagentActionDetailService {
    private final Ancestoriser ancestoriser;
    private final ReagentActionRepo reagentActionRepo;
    private final ReagentPlateRepo reagentPlateRepo;
    private final TagLayoutRepo tagLayoutRepo;

    @Autowired
    public ReagentActionDetailService(Ancestoriser ancestoriser,
                                      ReagentActionRepo reagentActionRepo, ReagentPlateRepo reagentPlateRepo,
                                      TagLayoutRepo tagLayoutRepo) {
        this.ancestoriser = ancestoriser;
        this.reagentActionRepo = reagentActionRepo;
        this.reagentPlateRepo = reagentPlateRepo;
        this.tagLayoutRepo = tagLayoutRepo;
    }

    /**
     * Makes reagent action details relevant to the given operation ids.
     * @param opIds op ids
     * @return a map from operation id to the list of reagent action details for that operation
     */
    public Map<Integer, List<ReagentActionDetail>> loadReagentTransfers(Collection<Integer> opIds) {
        List<ReagentAction> reagentActions = reagentActionRepo.findAllByOperationIdIn(opIds);
        return toDetailMap(reagentActions, ReagentAction::getOperationId);
    }

    /**
     * Makes reagent action details relevant to the given labware slot ids.
     * @param slotIds the slot ids
     * @return a map from slot id to the list of reagent action details for that slot
     */
    public Map<Integer, List<ReagentActionDetail>> loadReagentTransfersForSlotIds(Collection<Integer> slotIds) {
        List<ReagentAction> reagentActions = reagentActionRepo.findAllByDestinationIdIn(slotIds);
        return toDetailMap(reagentActions, ra -> ra.getDestination().getId());
    }

    /**
     * Makes reagent actions details for ancestors of the specified slots and samples.
     * @param slotSamples the slots and samples
     * @return a map from slot id to list of ancestral reagent actions for that slot
     */
    public Map<Integer, List<ReagentActionDetail>> loadAncestralReagentTransfers(
            Collection<Ancestoriser.SlotSample> slotSamples) {
        Ancestoriser.Ancestry ancestry = ancestoriser.findAncestry(slotSamples);
        Set<Integer> slotIds = ancestry.keySet().stream().map(ss -> ss.getSlot().getId()).collect(toSet());
        Map<Integer, List<ReagentActionDetail>> ancResults = loadReagentTransfersForSlotIds(slotIds);
        Map<Integer, List<ReagentActionDetail>> results = new HashMap<>(slotSamples.size());

        for (Ancestoriser.SlotSample ss : slotSamples) {
            ancestry.ancestors(ss).stream()
                    .map(ssa -> ancResults.get(ssa.getSlot().getId()))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .ifPresent(rads -> results.put(ss.getSlot().getId(), rads));
        }
        return results;
    }

    /**
     * Converts reagent actions to details, and puts them in a multivalued map.
     * @param reagentActions the reagent actions
     * @param keyFunction the function giving the map key
     * @param <K> the type of key for the map
     * @return a map of the given keys to the corresponding details
     */
    private <K> Map<K, List<ReagentActionDetail>> toDetailMap(Collection<ReagentAction> reagentActions,
                                                                Function<ReagentAction, K> keyFunction) {
        if (reagentActions.isEmpty()) {
            return Map.of();
        }
        Set<Integer> reagentPlateIds = reagentActions.stream()
                .map(ra -> ra.getReagentSlot().getPlateId())
                .collect(toSet());
        var reagentPlates = reagentPlateRepo.findAllById(reagentPlateIds);
        Map<Integer, ReagentPlate> reagentPlateMap = stream(reagentPlates)
                .collect(BasicUtils.inMap(ReagentPlate::getId));
        Map<Integer, TagLayout> tagLayoutMap = tagLayoutRepo.getMapByIdIn(
                reagentPlateMap.values().stream()
                        .map(ReagentPlate::getTagLayoutId)
                        .filter(Objects::nonNull)
                        .collect(toSet())
        );
        final Map<K, List<ReagentActionDetail>> map = new HashMap<>();
        for (var ra : reagentActions) {
            ReagentPlate rp = reagentPlateMap.get(ra.getReagentSlot().getPlateId());
            TagLayout tl = rp.getTagLayoutId()==null ? null : tagLayoutMap.get(rp.getTagLayoutId());
            Map<String, String> tagData = (tl==null ? null : tl.getTagData(ra.getReagentSlot().getAddress()));
            ReagentActionDetail rad = new ReagentActionDetail(rp.getBarcode(), rp.getPlateType(),
                    ra.getReagentSlot().getAddress(), ra.getDestination().getAddress(),
                    ra.getDestination().getLabwareId(), tagData);
            map.computeIfAbsent(keyFunction.apply(ra), k -> new ArrayList<>()).add(rad);
        }
        return map;
    }

    /**
     * Some presentable information about a reagent action
     */
    public static class ReagentActionDetail {
        public final String reagentPlateBarcode;
        public final String reagentPlateType;
        public final Address reagentSlotAddress;
        public final Address destSlotAddress;
        public final int destinationLabwareId;
        public final Map<String, String> tagData;

        public ReagentActionDetail(String reagentPlateBarcode, String reagentPlateType,
                                   Address reagentSlotAddress, Address destSlotAddress,
                                   int destinationLabwareId, Map<String, String> tagData) {
            this.reagentPlateBarcode = reagentPlateBarcode;
            this.reagentPlateType = reagentPlateType;
            this.reagentSlotAddress = reagentSlotAddress;
            this.destSlotAddress = destSlotAddress;
            this.destinationLabwareId = destinationLabwareId;
            this.tagData = nullToEmpty(tagData);
        }

        public Map<String, String> getTagData() {
            return this.tagData;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ReagentActionDetail that = (ReagentActionDetail) o;
            return (this.destinationLabwareId == that.destinationLabwareId
                    && Objects.equals(this.reagentPlateBarcode, that.reagentPlateBarcode)
                    && Objects.equals(this.reagentPlateType, that.reagentPlateType)
                    && Objects.equals(this.reagentSlotAddress, that.reagentSlotAddress)
                    && Objects.equals(this.destSlotAddress, that.destSlotAddress)
                    && Objects.equals(this.tagData, that.tagData));
        }

        @Override
        public int hashCode() {
            return Objects.hash(reagentPlateBarcode, reagentPlateType, reagentSlotAddress, destSlotAddress,
                    destinationLabwareId);
        }
    }
}
