package uk.ac.sanger.sccp.stan.service;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.UnreleaseRequest;
import uk.ac.sanger.sccp.stan.request.UnreleaseRequest.UnreleaseLabware;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 * @author dr6
 */
@Service
public class UnreleaseServiceImp implements UnreleaseService {
    private final LabwareValidatorFactory labwareValidatorFactory;
    private final LabwareRepo lwRepo;
    private final SlotRepo slotRepo;
    private final OperationTypeRepo opTypeRepo;
    private final OperationService opService;

    @Autowired
    public UnreleaseServiceImp(LabwareValidatorFactory labwareValidatorFactory,
                               LabwareRepo lwRepo, SlotRepo slotRepo, OperationTypeRepo opTypeRepo,
                               OperationService opService) {
        this.labwareValidatorFactory = labwareValidatorFactory;
        this.lwRepo = lwRepo;
        this.slotRepo = slotRepo;
        this.opTypeRepo = opTypeRepo;
        this.opService = opService;
    }

    @Override
    public OperationResult unrelease(User user, UnreleaseRequest request) throws ValidationException {
        requireNonNull(user, "User is null.");
        requireNonNull(request, "Request is null.");
        Set<String> problems = new LinkedHashSet<>();
        UCMap<Labware> labware = loadLabware(problems, request.getLabware());
        validateRequest(problems, labware, request.getLabware());

        OperationType opType = opTypeRepo.findByName("Unrelease").orElse(null);
        if (opType==null) {
            problems.add("Operation type \"Unrelease\" not found in database.");
        }

        if (!problems.isEmpty()) {
            throw new ValidationException("The unrelease request could not be validated.", problems);
        }

        return perform(user, request, opType, labware);
    }

    /**
     * Loads the labware and checks it can be unreleased
     * @param problems receptacle for any problems found
     * @param requestLabware the list specifying which labware to unrelease
     * @return a map of labware from its barcode
     */
    public UCMap<Labware> loadLabware(Collection<String> problems, Collection<UnreleaseLabware> requestLabware) {
        if (requestLabware.isEmpty()) {
            problems.add("No labware specified.");
            return new UCMap<>(0);
        }
        LabwareValidator val = labwareValidatorFactory.getValidator();
        List<String> barcodes = new ArrayList<>(requestLabware.size());
        boolean anyNull = false;
        for (UnreleaseLabware ul : requestLabware) {
            if (ul.getBarcode()==null) {
                anyNull = true;
            } else {
                barcodes.add(ul.getBarcode());
            }
        }
        if (anyNull) {
            problems.add("Null given as labware barcode.");
        }
        if (barcodes.isEmpty()) {
            return new UCMap<>(0);
        }
        val.loadLabware(lwRepo, barcodes);
        val.validateUnique();
        val.validateNonEmpty();
        val.validateState(lw -> !lw.isReleased(), "not released");
        val.validateState(Labware::isDestroyed, "destroyed");
        val.validateState(Labware::isDiscarded, "discarded");
        problems.addAll(val.getErrors());
        return UCMap.from(val.getLabware(), Labware::getBarcode);
    }

    /**
     * Checks that the requests make sense for the given labware. In particular, checks that the highest section
     * is appropriate for the indicated labware.
     * @param problems receptacle for problems found
     * @param labware a map to look up labware by barcode
     * @param requestLabware the requests to unrelease particular labware
     */
    public void validateRequest(Collection<String> problems, UCMap<Labware> labware, Collection<UnreleaseLabware> requestLabware) {
        for (UnreleaseLabware ul : requestLabware) {
            if (ul.getBarcode()!=null && ul.getHighestSection()!=null) {
                Labware lw = labware.get(ul.getBarcode());
                if (lw!=null) {
                    String blockProblem = highestSectionProblem(lw, ul.getHighestSection());
                    if (blockProblem!=null) {
                        problems.add(blockProblem);
                    }
                }
            }
        }
    }

    /**
     * Checks that it is valid to set the given labware's highest section to the given value.
     * Returns a string indicating the problem, if any.
     * @param lw the labware
     * @param highestSection the request new value for its highest section
     * @return a string indicating the problem; null if no problem was found.
     */
    public String highestSectionProblem(Labware lw, int highestSection) {
        Slot slot = lw.getFirstSlot();
        if (!slot.isBlock()) {
            return "Cannot set the highest section number from labware "+lw.getBarcode()+" because it is not a block.";
        }
        Integer oldValue = slot.getBlockHighestSection();
        if (oldValue!=null && oldValue > highestSection) {
            return String.format("For block %s, cannot reduce the highest section number from %s to %s.",
                    lw.getBarcode(), slot.getBlockHighestSection(), highestSection);
        }
        if (highestSection < 0) {
            return "Cannot set the highest section to a negative number.";
        }
        return null;
    }

    /**
     * Records unrelease operations, updates labware.
     * @param user the user responsible for the request
     * @param request the request to unrelease some labware
     * @param opType the unrelease operation type
     * @param labwareMap a map to look up the labware from its barcode
     * @return the operations and labware affected
     */
    public OperationResult perform(User user, UnreleaseRequest request, OperationType opType, UCMap<Labware> labwareMap) {
        List<Labware> labwareList = updateLabware(request.getLabware(), labwareMap);
        List<Operation> ops = recordOps(user, opType, labwareList);
        return new OperationResult(ops, labwareList);
    }

    /**
     * Updates the indicates labware.
     * Sets the labware to not be released, and updates its highest section where appropriate
     * @param requestLabware the request to update the labware
     * @param labwareMap a map to look up the labware from its barcode
     * @return the updated labware
     */
    @NotNull
    public List<Labware> updateLabware(List<UnreleaseLabware> requestLabware, UCMap<Labware> labwareMap) {
        List<Labware> labwareList = new ArrayList<>(requestLabware.size());
        List<Slot> slotsToUpdate = new ArrayList<>(requestLabware.size());

        for (UnreleaseLabware ul : requestLabware) {
            Labware lw = labwareMap.get(ul.getBarcode());
            lw.setReleased(false);
            if (ul.getHighestSection()!=null) {
                Slot slot = lw.getFirstSlot();
                if (slot.getBlockHighestSection()==null || slot.getBlockHighestSection() < ul.getHighestSection()) {
                    slot.setBlockHighestSection(ul.getHighestSection());
                    slotsToUpdate.add(slot);
                }
            }
            labwareList.add(lw);
        }

        if (!slotsToUpdate.isEmpty()) {
            slotRepo.saveAll(slotsToUpdate);
        }
        lwRepo.saveAll(labwareList);
        return labwareList;
    }

    /**
     * Records unrelease operations
     * @param user the user responsible for the operations
     * @param opType the type of operation
     * @param labware the labware
     * @return the new operations
     */
    List<Operation> recordOps(User user, OperationType opType, Collection<Labware> labware) {
        return labware.stream()
                .map(lw -> opService.createOperationInPlace(opType, user, lw, null, null))
                .collect(toList());
    }
}
