package uk.ac.sanger.sccp.stan.service;

import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OpPassFail;
import uk.ac.sanger.sccp.stan.request.OpPassFail.SlotPassFail;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.*;

import static java.util.stream.Collectors.toList;

/**
 * A service for looking up operations with pass/fail results
 * @author dr6
 */
@Service
public class PassFailQueryService {
    private final LabwareRepo lwRepo;
    private final OperationTypeRepo opTypeRepo;
    private final OperationRepo opRepo;
    private final ResultOpRepo resultOpRepo;
    private final OperationCommentRepo opCommentRepo;

    public PassFailQueryService(LabwareRepo lwRepo, OperationTypeRepo opTypeRepo, OperationRepo opRepo,
                                ResultOpRepo resultOpRepo, OperationCommentRepo opCommentRepo) {
        this.lwRepo = lwRepo;
        this.opTypeRepo = opTypeRepo;
        this.opRepo = opRepo;
        this.resultOpRepo = resultOpRepo;
        this.opCommentRepo = opCommentRepo;
    }

    /**
     * Gets the operation/pass/fails for a particular piece of labware and op type.
     * The barcode must be a valid labware barcode. The operation type must exist.
     * @param barcode the barcode of an existing piece of labware
     * @param operationName the name of an operation type
     * @return a list of the operations of the given type recorded on the specified piece of labware,
     *  along with the results for those operations
     */
    public List<OpPassFail> getPassFails(String barcode, String operationName) {
        Labware lw = lwRepo.getByBarcode(barcode);
        OperationType opType = opTypeRepo.getByName(operationName);
        List<Operation> ops = opRepo.findAllByOperationTypeAndDestinationLabwareIdIn(opType, List.of(lw.getId()));
        return ops.stream()
                .sorted(Comparator.comparing(Operation::getPerformed).thenComparing(Operation::getId))
                .map(op -> new OpPassFail(op, getSlotPassFails(lw, op.getId())))
                .collect(toList());
    }

    /**
     * Gets the results in each slot for the given op in the given labware.
     * The results are grouped by slot and result; and list the sample ids they apply to.
     * (In the expected case there will be one entry per slot.)
     * @param lw the labware to find results for
     * @param opId the id of the operation to look up results for
     * @return the results for the given op in the given labware
     */
    public List<SlotPassFail> getSlotPassFails(Labware lw, Integer opId) {
        List<ResultOp> ros = resultOpRepo.findAllByOperationIdIn(List.of(opId));
        if (ros.isEmpty()) {
            return List.of();
        }
        Map<Integer, Slot> slotMap = lw.getSlots().stream()
                .collect(BasicUtils.inMap(Slot::getId));
        var commentMap = getCommentMap(slotMap, opId);

        Map<SlotPassFail, Set<Integer>> spfMap = new HashMap<>();

        for (ResultOp ro : ros) {
            Slot slot = slotMap.get(ro.getSlotId());
            if (slot==null) {
                continue;
            }
            Address address = slot.getAddress();
            var opComs = commentMap.get(new AddressSampleId(address, ro.getSampleId()));
            String comment = (opComs==null ? null : String.join("\n", opComs));
            SlotPassFail spf = new SlotPassFail(address, ro.getResult(), comment, null);
            spfMap.computeIfAbsent(spf, k -> new HashSet<>()).add(ro.getSampleId());
        }

        spfMap.forEach((spf, sampleIds) -> spf.setSampleIds(sampleIds.stream().sorted().collect(toList())));
        return new ArrayList<>(spfMap.keySet());
    }

    /**
     * Gets comments for a particular piece of labware and given operation id.
     * Returns a map from address and sample id to all the distinct comments (string) recorded for that
     * combination. Typically there will not be multiple comments for a single combination.
     * Only keys for which comments were found are included in the map.
     * @param slotIdMap a map of slots from their ids
     * @param opId the id of the operation
     * @return a map from address and sample id to the set of comments for that combination
     */
    public Map<AddressSampleId, Set<String>> getCommentMap(Map<Integer, Slot> slotIdMap, Integer opId) {
        Map<AddressSampleId, Set<String>> map = new HashMap<>();
        for (OperationComment opCom : opCommentRepo.findAllByOperationIdIn(List.of(opId))) {
            Slot slot = slotIdMap.get(opCom.getSlotId());
            if (slot != null) {
                map.computeIfAbsent(new AddressSampleId(slot.getAddress(), opCom.getSampleId()), k -> new LinkedHashSet<>())
                        .add(opCom.getComment().getText());
            }
        }
        return map;
    }

    /**
     * A key for {@link #getCommentMap}.
     */
    static class AddressSampleId {
        final Address address;
        final Integer sampleId;

        public AddressSampleId(Address address, Integer sampleId) {
            this.address = address;
            this.sampleId = sampleId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AddressSampleId that = (AddressSampleId) o;
            return (Objects.equals(this.address, that.address)
                    && Objects.equals(this.sampleId, that.sampleId));
        }

        @Override
        public int hashCode() {
            return Objects.hash(address, sampleId);
        }
    }
}
