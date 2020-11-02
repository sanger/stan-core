package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.ActionRepo;
import uk.ac.sanger.sccp.stan.repo.OperationRepo;

import javax.persistence.EntityNotFoundException;
import java.util.List;

/**
 * @author dr6
 */
@Component
public class OperationService {
    private final OperationRepo opRepo;
    private final ActionRepo actionRepo;

    @Autowired
    public OperationService(OperationRepo opRepo, ActionRepo actionRepo) {
        this.opRepo = opRepo;
        this.actionRepo = actionRepo;
    }

    public Operation createOperation(OperationType operationType, User user, List<Action> actions) {
        Operation op = opRepo.save(new Operation(null, operationType, null, null, user));
        for (Action action : actions) {
            action.setOperationId(op.getId());
        }
        actionRepo.saveAll(actions);
        return opRepo.findById(op.getId()).orElseThrow(EntityNotFoundException::new);
    }

    public Operation createOperation(OperationType operationType, User user, Slot source, Slot dest, Sample sample) {
        Action action = new Action(null, null, source, dest, sample);
        return createOperation(operationType, user, List.of(action));
    }
}
