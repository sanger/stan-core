package uk.ac.sanger.sccp.stan.service.operation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;

import java.util.Comparator;
import java.util.List;

/**
 * @author dr6
 */
@Service
public class RecentOpServiceImp implements RecentOpService {
    private final LabwareRepo lwRepo;
    private final OperationTypeRepo opTypeRepo;
    private final OperationRepo opRepo;

    static final Comparator<Operation> OP_COMPARATOR = Comparator.comparing(Operation::getPerformed).thenComparing(Operation::getId);

    @Autowired
    public RecentOpServiceImp(LabwareRepo lwRepo, OperationTypeRepo opTypeRepo, OperationRepo opRepo) {
        this.lwRepo = lwRepo;
        this.opTypeRepo = opTypeRepo;
        this.opRepo = opRepo;
    }

    @Override
    public Operation findLatestOp(String barcode, String opName) {
        Labware lw = lwRepo.getByBarcode(barcode);
        OperationType opType = opTypeRepo.getByName(opName);
        List<Operation> ops = opRepo.findAllByOperationTypeAndDestinationLabwareIdIn(opType, List.of(lw.getId()));
        return ops.stream()
                .max(OP_COMPARATOR)
                .orElse(null);
    }
}
