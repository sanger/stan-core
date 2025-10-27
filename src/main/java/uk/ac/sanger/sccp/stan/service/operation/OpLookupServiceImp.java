package uk.ac.sanger.sccp.stan.service.operation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;

import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

/**
 * @author dr6
 */
@Service
public class OpLookupServiceImp implements OpLookupService {
    private final OperationTypeRepo opTypeRepo;
    private final LabwareRepo lwRepo;
    private final WorkRepo workRepo;
    private final OperationRepo opRepo;
    private final LabwareNoteRepo lwNoteRepo;

    @Autowired
    public OpLookupServiceImp(OperationTypeRepo opTypeRepo, LabwareRepo lwRepo, WorkRepo workRepo,
                              OperationRepo opRepo, LabwareNoteRepo lwNoteRepo) {
        this.opTypeRepo = opTypeRepo;
        this.lwRepo = lwRepo;
        this.workRepo = workRepo;
        this.opRepo = opRepo;
        this.lwNoteRepo = lwNoteRepo;
    }

    @Override
    public List<Operation> findOps(String opName, String barcode, String run, String workNumber) {
        OperationType opType = opTypeRepo.findByName(opName).orElse(null);
        if (opType==null) {
            return List.of();
        }
        Labware lw = lwRepo.findByBarcode(barcode).orElse(null);
        if (lw==null) {
            return List.of();
        }
        Work work;
        if (workNumber!=null) {
            work = workRepo.findByWorkNumber(workNumber).orElse(null);
            if (work==null) {
                return List.of();
            }
        } else {
            work = null;
        }
        List<Operation> ops = opRepo.findAllByOperationTypeAndDestinationLabwareIdIn(opType, List.of(lw.getId()));
        if (work != null) {
            ops = ops.stream().filter(op -> work.getOperationIds().contains(op.getId())).toList();
        }
        if (!ops.isEmpty() && run != null) {
            List<LabwareNote> lwNotes = lwNoteRepo.findAllByLabwareIdInAndName(List.of(lw.getId()), "run");
            Set<Integer> runOpIds = lwNotes.stream()
                    .filter(note -> note.getValue().equalsIgnoreCase(run))
                    .map(LabwareNote::getOperationId)
                    .collect(toSet());
            ops = ops.stream()
                    .filter(op -> runOpIds.contains(op.getId()))
                    .toList();
        }
        return ops;
    }
}
