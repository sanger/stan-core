package uk.ac.sanger.sccp.stan.service.flag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.FlagLabwareRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.service.OperationService;
import uk.ac.sanger.sccp.stan.service.ValidationException;
import uk.ac.sanger.sccp.stan.service.work.WorkService;

import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * @author dr6
 */
@Service
public class FlagLabwareServiceImp implements FlagLabwareService {
    static final int MAX_DESCRIPTION_LEN = 512;

    private final OperationService opService;
    private final WorkService workService;

    private final LabwareFlagRepo flagRepo;
    private final LabwareRepo lwRepo;
    private final OperationTypeRepo opTypeRepo;

    @Autowired
    public FlagLabwareServiceImp(OperationService opService, WorkService workService,
                                 LabwareFlagRepo flagRepo, LabwareRepo lwRepo, OperationTypeRepo opTypeRepo) {
        this.opService = opService;
        this.workService = workService;
        this.flagRepo = flagRepo;
        this.lwRepo = lwRepo;
        this.opTypeRepo = opTypeRepo;
    }

    @Override
    public OperationResult record(User user, FlagLabwareRequest request) throws ValidationException {
        final Collection<String> problems = new LinkedHashSet<>();
        if (user==null) {
            problems.add("No user specified.");
        }
        if (request==null) {
            problems.add("No request supplied.");
            throw new ValidationException(problems);
        }
        Work work = (nullOrEmpty(request.getWorkNumber()) ? null
                : workService.validateUsableWork(problems, request.getWorkNumber()));

        Labware lw = loadLabware(problems, request.getBarcode());
        String description = checkDescription(problems, request.getDescription());
        OperationType opType = loadOpType(problems);

        if (!problems.isEmpty()) {
            throw new ValidationException(problems);
        }

        return create(user, opType, lw, description, work);
    }

    /**
     * Loads the labware indicated. The labware does not need to be active to be flagged.
     * @param problems receptacle for problems
     * @param barcode the barcode of the labware to load
     * @return the labware found, or null
     */
    Labware loadLabware(Collection<String> problems, String barcode) {
        if (nullOrEmpty(barcode)) {
            problems.add("No labware barcode supplied.");
            return null;
        }
        var opt = lwRepo.findByBarcode(barcode);
        if (opt.isEmpty()) {
            problems.add("Unknown labware barcode: "+repr(barcode));
            return null;
        }
        Labware lw = opt.get();
        if (lw.isEmpty()) {
            problems.add("Labware "+lw.getBarcode()+" is empty.");
        }
        return opt.get();
    }

    /**
     * Sanitises and validates the flag description.
     * Sanitisation involves removing superfluous whitespace.
     * Validation involves checking the description is non-null and a suitable length.
     * @param problems receptacle for problems found
     * @param description the description to validate
     * @return the sanitised description, if any description was supplied
     */
    String checkDescription(Collection<String> problems, String description) {
        if (description!=null) {
            description = description.trim().replaceAll("\\s+", " ");
            if (!description.isEmpty()) {
                if (description.length() > MAX_DESCRIPTION_LEN) {
                    problems.add("Description too long.");
                }
                return description;
            }
        }
        problems.add("Missing flag description.");
        return null;
    }

    /**
     * Loads the appropriate op type
     * @param problems receptacle for problems
     * @return the operation type, if found
     */
    OperationType loadOpType(Collection<String> problems) {
        var opt = opTypeRepo.findByName("Flag labware");
        if (opt.isEmpty()) {
            problems.add("Flag labware operation type is missing.");
            return null;
        }
        return opt.get();
    }

    /**
     * Records a flag labware operation and records the specified flag
     * @param user the user responsible
     * @param opType the operation type to record
     * @param lw the labware being flagged
     * @param description the flag description
     * @param work work to link to operation (or null)
     * @return the labware and operation
     */
    OperationResult create(User user, OperationType opType, Labware lw, String description, Work work) {
        Operation op = opService.createOperationInPlace(opType, user, lw, null, null);
        LabwareFlag flag = new LabwareFlag(null, lw, description, user, op.getId());
        flagRepo.save(flag);
        if (work!=null) {
            workService.link(work, List.of(op));
        }
        return new OperationResult(List.of(op), List.of(lw));
    }
}
