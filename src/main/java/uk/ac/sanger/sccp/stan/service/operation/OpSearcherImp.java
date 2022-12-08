package uk.ac.sanger.sccp.stan.service.operation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.OperationRepo;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.Ancestry;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.SlotSample;

import java.util.*;

import static java.util.stream.Collectors.*;

/**
 * Service for finding the last op on given labware or its ancestors.
 * @author dr6
 */
@Service
public class OpSearcherImp implements OpSearcher {
    private final Ancestoriser ancestoriser;
    private final OperationRepo opRepo;

    @Autowired
    public OpSearcherImp(Ancestoriser ancestoriser, OperationRepo opRepo) {
        this.ancestoriser = ancestoriser;
        this.opRepo = opRepo;
    }

    @Override
    public Map<Integer, Operation> findLabwareOps(OperationType opType, Collection<Labware> labware) {
        List<SlotSample> slotSamples = labware.stream()
                .flatMap(SlotSample::stream)
                .collect(toList());
        Ancestry ancestry = ancestoriser.findAncestry(slotSamples);
        return findLabwareOps(opType, labware, ancestry);
    }

    /**
     * Finds the op of the given type for each given labware with the given ancestry.
     * @param opType the type of operation to find
     * @param labware the labware to find operations for
     * @param ancestry the ancestry of the labware
     * @return a map of labware id to operation
     */
    public Map<Integer, Operation> findLabwareOps(OperationType opType, Collection<Labware> labware, Ancestry ancestry) {
        Set<Integer> slotIds = ancestry.keySet().stream()
                .map(ss -> ss.getSlot().getId())
                .collect(toSet());
        List<Operation> ops = opRepo.findAllByOperationTypeAndDestinationSlotIdIn(opType, slotIds);
        if (ops.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Operation> destSlotOp = new HashMap<>(slotIds.size());
        ops.forEach(op -> op.getActions().forEach(ac -> destSlotOp.put(ac.getDestination().getId(), op)));
        Map<Integer, Operation> lwOp = new HashMap<>(labware.size());
        for (Labware lw : labware) {
            Operation op = selectOp(lw, ancestry, destSlotOp);
            if (op!=null) {
                lwOp.put(lw.getId(), op);
            }
        }
        return lwOp;
    }

    /**
     * Gets the greater of two comparable objects.
     * Objects are compared using their {@link Comparable#compareTo} method, with the
     * greater being selected.
     * If either is null, the other will be returned.
     * @param opA one of the objects
     * @param opB the other of the objects
     * @return the greater of the new objects
     */
    public <T extends Comparable<T>> T selectGreater(T opA, T opB) {
        if (opA==null) {
            return opB;
        }
        if (opB==null) {
            return opA;
        }
        return (opA.compareTo(opB) > 0 ? opA : opB);
    }

    /**
     * Selects latest generational op for the given labware and its ancestry.
     * @param lw the labware to find the operation for
     * @param ancestry the ancestry of the labware
     * @param destOp a map of destination slot id to operation
     * @return a latest generational op for the given labware and its ancestry, if any is found; otherwise null
     */
    public Operation selectOp(Labware lw, Ancestry ancestry, Map<Integer, Operation> destOp) {
        Collection<SlotSample> sss = SlotSample.stream(lw).collect(toList());
        final Set<SlotSample> seenSlotSamples = new HashSet<>();
        while (!sss.isEmpty()) {
            seenSlotSamples.addAll(sss);
            Operation foundOp = null;
            for (SlotSample ss : sss) {
                Operation op = destOp.get(ss.getSlot().getId());
                foundOp = selectGreater(op, foundOp);

            }
            if (foundOp!=null) {
                return foundOp;
            }
            sss = sss.stream()
                    .flatMap(ss -> ancestry.ancestors(ss).stream())
                    .filter(ss -> !seenSlotSamples.contains(ss))
                    .collect(toSet());
        }
        return null;
    }
}
