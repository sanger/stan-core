package uk.ac.sanger.sccp.stan.service.flag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.LabwareFlag.Priority;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.FlagLabwareRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.service.OperationService;
import uk.ac.sanger.sccp.stan.service.ValidationException;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.*;

import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

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

        List<Labware> lws = loadLabware(problems, request.getBarcodes());
        String description = checkDescription(problems, request.getDescription());
        if (request.getPriority()==null) {
            problems.add("No priority specified.");
        }
        OperationType opType = loadOpType(problems);

        if (!problems.isEmpty()) {
            throw new ValidationException(problems);
        }

        return create(user, opType, lws, description, work, request.getPriority());
    }

    /**
     * Loads the labware indicated. The labware does not need to be active to be flagged.
     * @param problems receptacle for problems
     * @param barcodes the barcodes of the labware to load
     * @return the labware found, or null
     */
    List<Labware> loadLabware(Collection<String> problems, List<String> barcodes) {
        if (nullOrEmpty(barcodes)) {
            problems.add("No labware barcodes supplied.");
            return null;
        }
        if (barcodes.stream().anyMatch(BasicUtils::nullOrEmpty)) {
            problems.add("Barcodes array has missing elements.");
            return null;
        }
        List<Labware> labware = lwRepo.findByBarcodeIn(barcodes);
        Set<String> foundBarcodes = labware.stream().map(lw -> lw.getBarcode().toUpperCase())
                .collect(toSet());
        List<String> missing = barcodes.stream()
                .filter(bc -> !foundBarcodes.contains(bc.toUpperCase()))
                .map(BasicUtils::repr)
                .toList();
        if (!missing.isEmpty()) {
            problems.add("Unknown labware barcode: "+missing);
        }
        List<String> bcOfEmptyLabware = labware.stream()
                .filter(Labware::isEmpty)
                .map(Labware::getBarcode)
                .toList();
        if (!bcOfEmptyLabware.isEmpty()) {
            problems.add("Labware is empty: "+bcOfEmptyLabware);
        }
        return labware;
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
     * Records flag labware operations and records the specified flag
     * @param user the user responsible
     * @param opType the operation type to record
     * @param labware the labware being flagged
     * @param description the flag description
     * @param work work to link to operations (or null)
     * @return the labware and operations
     */
    OperationResult create(User user, OperationType opType, List<Labware> labware, String description, Work work, Priority priority) {
        List<Operation> ops = new ArrayList<>(labware.size());
        List<LabwareFlag> flags = new ArrayList<>(labware.size());
        for (Labware lw : labware) {
            Operation op = opService.createOperationInPlace(opType, user, lw, null, null);
            LabwareFlag flag = new LabwareFlag(null, lw, description, user, op.getId(), priority);
            ops.add(op);
            flags.add(flag);
        }
        flagRepo.saveAll(flags);
        if (work!=null) {
            workService.link(work, ops);
        }
        return new OperationResult(ops, labware);
    }
}
