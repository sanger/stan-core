package uk.ac.sanger.sccp.stan.service.history;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentAction;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentPlate;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toSet;

/**
 * Service to describe the details of reagent transfers for particular operations.
 * @author dr6
 */
@Service
public class ReagentActionDetailService {
    private final ReagentActionRepo reagentActionRepo;
    private final ReagentPlateRepo reagentPlateRepo;

    @Autowired
    public ReagentActionDetailService(ReagentActionRepo reagentActionRepo, ReagentPlateRepo reagentPlateRepo) {
        this.reagentActionRepo = reagentActionRepo;
        this.reagentPlateRepo = reagentPlateRepo;
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
     * Converts reagent actions to details, and puts them in a multi-valued map.
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
        Map<Integer, ReagentPlate> reagentPlateMap = StreamSupport.stream(reagentPlates.spliterator(), false)
                .collect(BasicUtils.toMap(ReagentPlate::getId));
        final Map<K, List<ReagentActionDetail>> map = new HashMap<>();
        for (var ra : reagentActions) {
            ReagentPlate rp = reagentPlateMap.get(ra.getReagentSlot().getPlateId());
            ReagentActionDetail rad = new ReagentActionDetail(rp.getBarcode(), rp.getPlateType(),
                    ra.getReagentSlot().getAddress(), ra.getDestination().getAddress(),
                    ra.getDestination().getLabwareId());
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

        public ReagentActionDetail(String reagentPlateBarcode, String reagentPlateType,
                                   Address reagentSlotAddress, Address destSlotAddress,
                                   int destinationLabwareId) {
            this.reagentPlateBarcode = reagentPlateBarcode;
            this.reagentPlateType = reagentPlateType;
            this.reagentSlotAddress = reagentSlotAddress;
            this.destSlotAddress = destSlotAddress;
            this.destinationLabwareId = destinationLabwareId;
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
                    && Objects.equals(this.destSlotAddress, that.destSlotAddress));
        }

        @Override
        public int hashCode() {
            return Objects.hash(reagentPlateBarcode, reagentPlateType, reagentSlotAddress, destSlotAddress,
                    destinationLabwareId);
        }
    }
}
