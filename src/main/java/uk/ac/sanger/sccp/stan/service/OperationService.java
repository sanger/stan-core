package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.ActionRepo;
import uk.ac.sanger.sccp.stan.repo.OperationRepo;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;

/**
 * @author dr6
 */
@Service
@Transactional
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

    public Operation createOperation(OperationType operationType, User user, List<Action> actions) {
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
