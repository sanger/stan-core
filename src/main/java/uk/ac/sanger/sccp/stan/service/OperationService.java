package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.ActionRepo;
import uk.ac.sanger.sccp.stan.repo.OperationRepo;

import javax.persistence.EntityManager;
import java.util.List;
import java.util.function.Consumer;

import static java.util.stream.Collectors.toList;

/**
 * Service to create and record {@link Operation operations}.
 * @author dr6
 */
@Service
public class OperationService {
    private final EntityManager entityManager;
    private final OperationRepo opRepo;
    private final ActionRepo actionRepo;

    @Autowired
    public OperationService(EntityManager entityManager, OperationRepo opRepo, ActionRepo actionRepo) {
        this.entityManager = entityManager;
        this.opRepo = opRepo;
        this.actionRepo = actionRepo;
    }

    /**
     * Records an operation with the specified actions.
     * @param opType the type of operation
     * @param user the user responsible
     * @param actions the actions for the operation
     * @param planId the id of the plan associated with this operation
     * @return a new instance of operation
     */
    public Operation createOperation(OperationType opType, User user, List<Action> actions, Integer planId) {
        return createOperation(opType, user, actions, planId, null);
    }

    /**
     * Records an operation with the specified actions.
     * @param opType the type of operation
     * @param user the user responsible
     * @param actions the actions for the operation
     * @param planId the id of the plan associated with this operation
     * @param operationModifier a function to call on the operation before it is saved
     * @return a new instance of operation
     */
    public Operation createOperation(OperationType opType, User user, List<Action> actions, Integer planId,
                                     Consumer<Operation> operationModifier) {
        if (actions.isEmpty()) {
            throw new IllegalArgumentException("No actions received to create operation.");
        }
        Operation op = new Operation(null, opType, null, null, user, planId);
        if (operationModifier!=null) {
            operationModifier.accept(op);
        }
        op = opRepo.save(op);
        for (Action action : actions) {
            action.setOperationId(op.getId());
        }
        actionRepo.saveAll(actions);
        entityManager.refresh(op);
        return op;
    }

    /**
     * Creates a new operation with an action in one slot of one item of labware
     * @param operationType the operation type
     * @param user the user responsible for the operation
     * @param slot the slot where the operation takes place
     * @param sample the sample taking place in the operation
     * @return the new operation
     */
    public Operation createOperationInPlace(OperationType operationType, User user, Slot slot, Sample sample) {
        Action action = new Action(null, null, slot, slot, sample, sample);
        return createOperation(operationType, user, List.of(action), null);
    }

    /**
     * Creates a new in-place operation with an action for each sample in each slot of the given labware
     * @param opType the operation type
     * @param user the user responsible for the operation
     * @param labware the labware where the operation takes place
     * @param planId the id of the plan operation (if any)
     * @param operationModifier a function to call on the operation before it is created (if any)
     * @return the newly created operation
     */
    public Operation createOperationInPlace(OperationType opType, User user, Labware labware, Integer planId,
                                            Consumer<Operation> operationModifier) {
        List<Action> actions = labware.getSlots().stream().flatMap(slot -> slot.getSamples().stream()
                        .map(sam -> new Action(null, null, slot, slot, sam, sam)))
                .collect(toList());
        return createOperation(opType, user, actions, planId, operationModifier);
    }
}
