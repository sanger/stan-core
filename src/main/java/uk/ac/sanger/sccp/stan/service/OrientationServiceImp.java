package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.OrientationRequest;
import uk.ac.sanger.sccp.stan.service.work.WorkService;

import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * @author dr6
 */
@Service
public class OrientationServiceImp implements OrientationService {

    public static final String OP_NAME = "Orientation QC";
    private final LabwareValidatorFactory lwValFactory;
    private final WorkService workService;
    private final OperationService opService;
    private final OperationTypeRepo opTypeRepo;
    private final LabwareRepo lwRepo;
    private final LabwareNoteRepo lwNoteRepo;

    @Autowired
    public OrientationServiceImp(LabwareValidatorFactory lwValFactory,
                                 WorkService workService, OperationService opService,
                                 OperationTypeRepo opTypeRepo, LabwareRepo lwRepo, LabwareNoteRepo lwNoteRepo) {
        this.lwValFactory = lwValFactory;
        this.workService = workService;
        this.opService = opService;
        this.opTypeRepo = opTypeRepo;
        this.lwRepo = lwRepo;
        this.lwNoteRepo = lwNoteRepo;
    }

    @Override
    public OperationResult perform(User user, OrientationRequest request) throws ValidationException {
        Collection<String> problems = new LinkedHashSet<>();
        if (user==null) {
            problems.add("No user supplied.");
        }
        if (request==null) {
            problems.add("No request supplied.");
            throw new ValidationException(problems);
        }

        Labware lw = checkLabware(problems, request.getBarcode());
        OperationType opType = loadOpType(problems);
        Work work = workService.validateUsableWork(problems, request.getWorkNumber());


        if (!problems.isEmpty()) {
            throw new ValidationException(problems);
        }

        return record(user, opType, lw, work, request.isCorrect());
    }

    /**
     * Checks that a labware barcode is supplied, valid, and that the labware is suitable
     * @param problems receptacle for problems
     * @param barcode the barcode to look up
     * @return the labware found, or null
     */
    Labware checkLabware(Collection<String> problems, String barcode) {
        if (nullOrEmpty(barcode)) {
            problems.add("No barcode supplied.");
            return null;
        }
        LabwareValidator val = lwValFactory.getValidator();
        val.setSingleSample(true);
        val.setBlockRequired(true);
        List<Labware> lws = val.loadLabware(lwRepo, List.of(barcode));
        val.validateSources();
        problems.addAll(val.getErrors());
        return (lws.isEmpty() ? null : lws.get(0));
    }

    /**
     * Gets the required op type
     * @param problems receptacle for problems
     * @return the operation type
     */
    OperationType loadOpType(Collection<String> problems) {
        var opTypeOpt = opTypeRepo.findByName(OP_NAME);
        if (opTypeOpt.isEmpty()) {
            problems.add("Operation type not available: "+OP_NAME+".");
            return null;
        }
        return opTypeOpt.get();
    }

    /**
     * Records the operation and the orientation
     * @param user the user responsible
     * @param opType the type of operation to record
     * @param lw the labware to record the operation on
     * @param work the work to link to the operation
     * @param correct whether the orientation is correct or not
     * @return the labware and created operation
     */
    OperationResult record(User user, OperationType opType, Labware lw, Work work, boolean correct) {
        Operation op = opService.createOperationInPlace(opType, user, lw, null, null);
        workService.link(work, List.of(op));
        LabwareNote note = new LabwareNote(null, lw.getId(), op.getId(), "Orientation", correct ? "correct": "incorrect");
        lwNoteRepo.save(note);
        return new OperationResult(List.of(op), List.of(lw));
    }

}
