package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.service.operation.OpSearcher;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;

import static java.util.stream.Collectors.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * Base class for some other result services
 * @author dr6
 */
public abstract class BaseResultService {
    protected final LabwareValidatorFactory labwareValidatorFactory;
    protected final OperationTypeRepo opTypeRepo;
    protected final OperationRepo opRepo;
    protected final LabwareRepo lwRepo;
    protected final OpSearcher opSearcher;

    protected BaseResultService(LabwareValidatorFactory labwareValidatorFactory,
                                OperationTypeRepo opTypeRepo, OperationRepo opRepo, LabwareRepo lwRepo,
                                OpSearcher opSearcher) {
        this.labwareValidatorFactory = labwareValidatorFactory;
        this.opTypeRepo = opTypeRepo;
        this.opRepo = opRepo;
        this.lwRepo = lwRepo;
        this.opSearcher = opSearcher;
    }

    /**
     * Loads an operation type by name
     * @param problems receptacle for problems
     * @param opTypeName the name of the op type
     * @return the op type loaded
     */
    public OperationType loadOpType(Collection<String> problems, String opTypeName) {
        Optional<OperationType> opt = opTypeRepo.findByName(opTypeName);
        if (opt.isEmpty()) {
            problems.add("Unknown operation type: "+repr(opTypeName));
            return null;
        }
        return opt.get();
    }

    /**
     * Loads the labware indicated by the given barcodes, using {@link LabwareValidator}.
     * Validates that the labware are unique, in a usable state, and nonempty.
     * @param problems receptacle for problems
     * @param barcodes the barcodes to load
     * @return the labware found, mapped from their barcodes
     */
    public UCMap<Labware> loadLabware(Collection<String> problems, Collection<String> barcodes) {
        LabwareValidator validator = labwareValidatorFactory.getValidator();
        validator.setUniqueRequired(true);
        validator.loadLabware(lwRepo, barcodes);
        validator.validateSources();
        problems.addAll(validator.getErrors());
        return UCMap.from(validator.getLabware(), Labware::getBarcode);
    }

    /**
     * This is used when making the labware-op map to decide whether the op under consideration
     * takes precedence over the current op listed (if any)
     * @see Operation#compareTo(Operation)
     * @param a the op under consideration
     * @param b the op already listed (or null)
     * @return True if any of the following:<ul>
     *     <li>{@code b} is null</li>
     *     <li>the timestamp of {@code a} is later than that of {@code b}</li>
     *     <li>they have the same timestamp and {@code a} has a higher ID</li>
     * </ul>
     * False otherwise
     */
    public boolean supersedes(Operation a, Operation b) {
        return (b==null || a.compareTo(b) > 0);
    }

    /**
     * Makes a map of labware id to operation from the given operations.
     * Where a labware id is linked to multiple operations, the latest op is selected.
     * @see #supersedes(Operation, Operation)
     * @param ops the operations
     * @return a map of labware id to operation id
     */
    public Map<Integer, Operation> makeLabwareOpMap(Collection<Operation> ops) {
        Map<Integer, Operation> opMap = new HashMap<>(ops.size());
        for (Operation op : ops) {
            Set<Integer> labwareIds = op.getActions().stream()
                    .map(a -> a.getDestination().getLabwareId())
                    .collect(toSet());
            for (Integer lwId : labwareIds) {
                if (supersedes(op, opMap.get(lwId))) {
                    opMap.put(lwId, op);
                }
            }
        }
        return opMap;
    }

    /**
     * Looks up the latest operations of the given type on each of the given labware (as the destination).
     * If any labware do not have a matching operation, then a problem will be added that includes a list of those barcodes.
     * Where multiple operations match for the same item of labware, {@link #supersedes} is used to determine
     * which should be kept.
     * @param problems receptacle for problems found
     * @param opType the type of op to look up
     * @param labware the labware that is the destinations of the operations
     * @param required whether the operation being not found constitutes a problem
     * @return a map from labware id to operation id
     */
    public Map<Integer, Integer> lookUpLatestOpIds(Collection<String> problems, OperationType opType,
                                                   Collection<Labware> labware, boolean required) {
        return lookUpLatestOps(problems, opType, labware, required).entrySet().stream()
                .collect(toMap(Map.Entry::getKey, e -> e.getValue().getId()));
    }

    /**
     * Looks up the latest operations of the given type on each of the given labware (as the destination).
     * If any labware do not have a matching operation, then a problem will be added that includes a list of those barcodes.
     * Where multiple operations match for the same item of labware, {@link #supersedes} is used to determine
     * which should be kept.
     * @param problems receptacle for problems found
     * @param opType the type of op to look up
     * @param labware the labware that is the destinations of the operations
     * @param required whether the operation being not found constitutes a problem
     * @return a map from labware id to operation
     */
    public Map<Integer, Operation> lookUpLatestOps(Collection<String> problems, OperationType opType,
                                                   Collection<Labware> labware, boolean required) {
        Set<Integer> labwareIds = labware.stream().map(Labware::getId).collect(toSet());
        List<Operation> ops = opRepo.findAllByOperationTypeAndDestinationLabwareIdIn(opType, labwareIds);
        Map<Integer, Operation> opsMap = makeLabwareOpMap(ops);
        List<String> unmatchedBarcodes = labware.stream()
                .filter(lw -> !opsMap.containsKey(lw.getId()))
                .map(Labware::getBarcode)
                .collect(toList());
        if (required && !unmatchedBarcodes.isEmpty()) {
            problems.add("No "+opType.getName()+" operation has been recorded on the following labware: "+unmatchedBarcodes);
        }
        return opsMap;
    }

    /**
     * Looks up the latest operations of the given type on each of the given labware (as the destination).
     * If any labware do not have a matching operation, then a problem will be added that includes a list of those barcodes.
     * Where multiple operations match for the same item of labware, {@link #supersedes} is used to determine
     * which should be kept.
     * @param problems receptacle for problems found
     * @param opType the type of op to look up
     * @param labware the labware that is the destinations of the operations
     * @param required whether the operation being not found constitutes a problem
     * @return a map from labware id to operation id
     */
    public Map<Integer, Integer> lookUpAncestralOpIds(Collection<String> problems, OperationType opType,
                                                      Collection<Labware> labware, boolean required) {
        Map<Integer, Operation> opMap = opSearcher.findLabwareOps(opType, labware);
        if (required) {
            List<String> missing = labware.stream()
                    .filter(lw -> opMap.get(lw.getId())==null)
                    .map(Labware::getBarcode)
                    .collect(toList());
            if (!missing.isEmpty()) {
                problems.add("No "+opType.getName()+" operation found on labware: "+missing);
            }
        }
        return opMap.entrySet().stream()
                .filter(e -> e.getValue()!=null)
                .collect(toMap(Map.Entry::getKey, e -> e.getValue().getId()));
    }

}
