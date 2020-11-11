package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.ActionRepo;
import uk.ac.sanger.sccp.stan.repo.OperationRepo;

import javax.persistence.EntityManager;
import java.util.List;

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
     * @param operationType the type of operation
     * @param user the user responsible
     * @param actions the actions for the operation
     * @return a new instance of operation
     */
    public Operation createOperation(OperationType operationType, User user, List<Action> actions) {
        if (actions.isEmpty()) {
            throw new IllegalArgumentException("No actions received to create operation.");
        }
        Operation op = opRepo.save(new Operation(null, operationType, null, null, user));
        for (Action action : actions) {
            action.setOperationId(op.getId());
        }
        actionRepo.saveAll(actions);
        entityManager.refresh(op);
        return op;
    }

    public Operation createOperation(OperationType operationType, User user, Slot source, Slot dest, Sample sample) {
        Action action = new Action(null, null, source, dest, sample);
        return createOperation(operationType, user, List.of(action));
    }
}
