package uk.ac.sanger.sccp.stan.service.history;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentAction;
import uk.ac.sanger.sccp.stan.repo.*;

import java.util.*;

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
     * Creates reagent action details relevant to the given operation ids.
     * @param opIds op ids
     * @return a map from operation id to the list of reagent action details for that operation
     */
    public Map<Integer, List<ReagentActionDetail>> loadReagentTransfers(Collection<Integer> opIds) {
        List<ReagentAction> reagentActions = reagentActionRepo.findAllByOperationIdIn(opIds);
        if (reagentActions.isEmpty()) {
            return Map.of();
        }
        Set<Integer> reagentPlateIds = reagentActions.stream()
                .map(ra -> ra.getReagentSlot().getPlateId())
                .collect(toSet());
        var reagentPlates = reagentPlateRepo.findAllById(reagentPlateIds);
        Map<Integer, String> reagentPlateBarcodes = new HashMap<>(reagentPlateIds.size());
        for (var reagentPlate : reagentPlates) {
            reagentPlateBarcodes.put(reagentPlate.getId(), reagentPlate.getBarcode());
        }
        Map<Integer, List<ReagentActionDetail>> map = new HashMap<>();
        for (var ra : reagentActions) {
            ReagentActionDetail rad = new ReagentActionDetail(reagentPlateBarcodes.get(ra.getReagentSlot().getPlateId()),
                    ra.getReagentSlot().getAddress(), ra.getDestination().getAddress(), ra.getDestination().getLabwareId());
            map.computeIfAbsent(ra.getOperationId(), k -> new ArrayList<>()).add(rad);
        }
        return map;
    }

    /**
     * Some presentable information about a reagent action
     */
    static class ReagentActionDetail {
        String reagentPlateBarcode;
        Address reagentSlotAddress;
        Address destSlotAddress;
        int destinationLabwareId;

        ReagentActionDetail(String reagentPlateBarcode, Address reagentSlotAddress, Address destSlotAddress,
                            int destinationLabwareId) {
            this.reagentPlateBarcode = reagentPlateBarcode;
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
                    && Objects.equals(this.reagentSlotAddress, that.reagentSlotAddress)
                    && Objects.equals(this.destSlotAddress, that.destSlotAddress));
        }

        @Override
        public int hashCode() {
            return Objects.hash(reagentPlateBarcode, reagentSlotAddress, destSlotAddress, destinationLabwareId);
        }
    }
}
