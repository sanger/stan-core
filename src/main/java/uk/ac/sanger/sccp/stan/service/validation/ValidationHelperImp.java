package uk.ac.sanger.sccp.stan.service.validation;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * @author dr6
 */
public class ValidationHelperImp implements ValidationHelper {
    private final LabwareValidatorFactory lwValFactory;
    private final OperationTypeRepo opTypeRepo;
    private final LabwareRepo lwRepo;
    private final EquipmentRepo equipmentRepo;
    private final WorkService workService;
    private final CommentValidationService commentValidationService;

    private final Set<String> problems = new LinkedHashSet<>();

    public ValidationHelperImp(LabwareValidatorFactory lwValFactory,
                               OperationTypeRepo opTypeRepo, LabwareRepo lwRepo, EquipmentRepo equipmentRepo,
                               WorkService workService, CommentValidationService commentValidationService) {
        this.lwValFactory = lwValFactory;
        this.opTypeRepo = opTypeRepo;
        this.equipmentRepo = equipmentRepo;
        this.lwRepo = lwRepo;
        this.workService = workService;
        this.commentValidationService = commentValidationService;
    }

    @Override
    public Set<String> getProblems() {
        return this.problems;
    }

    @Override
    public OperationType checkOpType(String opName, Collection<OperationTypeFlag> expectedFlags,
                                     Collection<OperationTypeFlag> expectedNotFlags,
                                     Predicate<OperationType> opTypePredicate) {
        if (nullOrEmpty(opName)) {
            addProblem("Operation type not specified.");
            return null;
        }
        OperationType opType = opTypeRepo.findByName(opName).orElse(null);
        if (opType==null) {
            addProblem("Unknown operation type: "+repr(opName));
            return null;
        }
        if ((expectedFlags!=null && expectedFlags.stream().anyMatch(flag -> !opType.has(flag)))
            || (expectedNotFlags!=null && expectedNotFlags.stream().anyMatch(opType::has))
            || (opTypePredicate!=null && !opTypePredicate.test(opType))) {
            addProblem("Operation type "+opType.getName()+" cannot be used in this operation.");
        }
        return opType;
    }

    @Override
    public UCMap<Labware> checkLabware(Collection<String> barcodes) {
        if (nullOrEmpty(barcodes)) {
            addProblem("No barcodes specified.");
            return new UCMap<>(0);
        }
        if (barcodes.stream().anyMatch(BasicUtils::nullOrEmpty)) {
            addProblem("Barcode missing.");
            barcodes = barcodes.stream()
                    .filter(bc -> !nullOrEmpty(bc))
                    .collect(toList());
            if (barcodes.isEmpty()) {
                return new UCMap<>(0);
            }
        }
        LabwareValidator val = lwValFactory.getValidator();
        val.loadLabware(lwRepo, barcodes);
        val.validateSources();
        problems.addAll(val.getErrors());
        return UCMap.from(val.getLabware(), Labware::getBarcode);
    }

    @Override
    public UCMap<Work> checkWork(Collection<String> workNumbers) {
        return workService.validateUsableWorks(this.problems, workNumbers);
    }

    @Override
    public Map<Integer, Comment> checkCommentIds(Stream<Integer> commentIdStream) {
        var comments = commentValidationService.validateCommentIds(problems, commentIdStream);
        return comments.stream().collect(inMap(Comment::getId));
    }

    @Override
    public void checkTimestamp(LocalDateTime timestamp, LocalDate today, Collection<Labware> labware, LocalDateTime priorOpTime) {
        if (timestamp!=null) {
            if (today!=null && timestamp.toLocalDate().isAfter(today)) {
                addProblem("The specified time is in the future.");
            } else if (priorOpTime != null && priorOpTime.isAfter(timestamp)) {
                addProblem("The specified time is before the preceding operation.");
            } else if (labware != null) {
                labware.stream().filter(lw -> lw.getCreated().isAfter(timestamp))
                        .findAny()
                        .ifPresent(lw -> addProblem("The specified timestamp is before labware " + lw.getBarcode() + " was created."));
            }
        }
    }

    @Override
    public Equipment checkEquipment(Integer equipmentId, String category, boolean required) {
        if (equipmentId == null) {
           if (required) {
               problems.add("No equipment id specified.");
           }
           return null;
        }
        Optional<Equipment> opt = equipmentRepo.findById(equipmentId);
        if (opt.isEmpty()) {
            problems.add("Unknown equipment id: "+equipmentId);
            return null;
        }
        Equipment equipment = opt.get();
        if (category!=null && !equipment.getCategory().equalsIgnoreCase(category)) {
            problems.add(String.format("Equipment %s (%s) cannot be used in this operation.",
                    equipment.getName(), equipment.getCategory()));
        }
        if (!equipment.isEnabled()) {
            problems.add("Equipment "+equipment+" is disabled.");
        }
        return equipment;
    }


    public void addProblem(String problem) {
        this.problems.add(problem);
    }
}
