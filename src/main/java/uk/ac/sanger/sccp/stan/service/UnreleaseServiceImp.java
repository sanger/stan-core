package uk.ac.sanger.sccp.stan.service;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.UnreleaseRequest;
import uk.ac.sanger.sccp.stan.request.UnreleaseRequest.UnreleaseLabware;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;

/**
 * @author dr6
 */
@Service
public class UnreleaseServiceImp implements UnreleaseService {
    private final LabwareValidatorFactory labwareValidatorFactory;
    private final LabwareRepo lwRepo;
    private final SampleRepo sampleRepo;
    private final OperationTypeRepo opTypeRepo;
    private final OperationService opService;
    private final WorkService workService;

    @Autowired
    public UnreleaseServiceImp(LabwareValidatorFactory labwareValidatorFactory,
                               LabwareRepo lwRepo, SampleRepo sampleRepo, OperationTypeRepo opTypeRepo,
                               OperationService opService, WorkService workService) {
        this.labwareValidatorFactory = labwareValidatorFactory;
        this.lwRepo = lwRepo;
        this.sampleRepo = sampleRepo;
        this.opTypeRepo = opTypeRepo;
        this.opService = opService;
        this.workService = workService;
    }

    @Override
    public OperationResult unrelease(User user, UnreleaseRequest request) throws ValidationException {
        requireNonNull(user, "User is null.");
        requireNonNull(request, "Request is null.");
        Set<String> problems = new LinkedHashSet<>();
        UCMap<Labware> labware = loadLabware(problems, request.getLabware());
        UCMap<Work> labwareWork = loadLabwareWork(problems, request.getLabware());
        validateRequest(problems, labware, request.getLabware());

        OperationType opType = opTypeRepo.findByName("Unrelease").orElse(null);
        if (opType==null) {
            problems.add("Operation type \"Unrelease\" not found in database.");
        }

        if (!problems.isEmpty()) {
            throw new ValidationException("The unrelease request could not be validated.", problems);
        }

        return perform(user, request, opType, labware, labwareWork);
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
     * Loads and validates the works for any work numbers specified.
     * @param problems receptacle for problems
     * @param requestLabware the relevant parts of the request
     * @return a map of work from the labware barcode
     */
    public UCMap<Work> loadLabwareWork(Collection<String> problems, Collection<UnreleaseLabware> requestLabware) {
        Set<String> workNumbers = requestLabware.stream()
                .map(UnreleaseLabware::getWorkNumber)
                .filter(Objects::nonNull)
                .collect(toSet());
        UCMap<Work> workMap = workService.validateUsableWorks(problems, workNumbers);
        UCMap<Work> lwWorkMap = new UCMap<>();
        for (UnreleaseLabware r : requestLabware) {
            Work work = workMap.get(r.getWorkNumber());
            if (work!=null) {
                lwWorkMap.put(r.getBarcode(), work);
            }
        }
        return lwWorkMap;
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
        Integer oldValue = lw.getSlots().stream()
                .flatMap(slot -> slot.getSamples().stream())
                .map(Sample::getBlockHighestSection)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (oldValue == null) {
            return "Cannot set the highest section number from labware "+lw.getBarcode()+" because it is not a block.";
        }
        if (highestSection < 0) {
            return "Cannot set the highest section to a negative number.";
        }
        if (oldValue > highestSection) {
            return String.format("For block %s, cannot reduce the highest section number from %s to %s.",
                    lw.getBarcode(), oldValue, highestSection);
        }
        return null;
    }

    /**
     * Records unrelease operations, updates labware.
     * @param user the user responsible for the request
     * @param request the request to unrelease some labware
     * @param opType the unrelease operation type
     * @param labwareMap a map to look up the labware from its barcode
     * @param labwareWork a map to look up work for each labware barcode
     * @return the operations and labware affected
     */
    public OperationResult perform(User user, UnreleaseRequest request, OperationType opType,
                                   UCMap<Labware> labwareMap, UCMap<Work> labwareWork) {
        List<Labware> labwareList = updateLabware(request.getLabware(), labwareMap);
        List<Operation> ops = recordOps(user, opType, labwareList, labwareWork);

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
        Set<Sample> samplesToUpdate = new HashSet<>();

        for (UnreleaseLabware ul : requestLabware) {
            Labware lw = labwareMap.get(ul.getBarcode());
            lw.setReleased(false);
            if (ul.getHighestSection()!=null) {
                for (Slot slot : lw.getSlots()) {
                    for (Sample sample : slot.getSamples()) {
                        if (sample.isBlock() && !sample.getBlockHighestSection().equals(ul.getHighestSection())) {
                            sample.setBlockHighestSection(ul.getHighestSection());
                            samplesToUpdate.add(sample);
                        }
                    }
                }
            }
            labwareList.add(lw);
        }

        if (!samplesToUpdate.isEmpty()) {
            sampleRepo.saveAll(new ArrayList<>(samplesToUpdate));
        }
        lwRepo.saveAll(labwareList);
        return labwareList;
    }

    /**
     * Records unrelease operations. Links them to works as specified.
     * @param user the user responsible for the operations
     * @param opType the type of operation
     * @param labware the labware
     * @param labwareWorkMap work for each labware barcode
     * @return the new operations
     */
    List<Operation> recordOps(User user, OperationType opType, Collection<Labware> labware,
                              UCMap<Work> labwareWorkMap) {
        List<Operation> ops = new ArrayList<>(labware.size());
        for (Labware lw : labware) {
            Operation op = opService.createOperationInPlace(opType, user, lw, null, null);
            Work work = labwareWorkMap.get(lw.getBarcode());
            if (work != null) {
                workService.link(work, List.of(op));
            }
            ops.add(op);
        }
        return ops;
    }
}
