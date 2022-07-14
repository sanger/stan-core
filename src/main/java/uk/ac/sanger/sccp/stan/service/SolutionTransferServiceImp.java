package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.SolutionTransferRequest;
import uk.ac.sanger.sccp.stan.request.SolutionTransferRequest.SolutionTransferLabware;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * @author dr6
 */
@Service
public class SolutionTransferServiceImp implements SolutionTransferService {
    private final SolutionRepo solutionRepo;
    private final OperationSolutionRepo opSolRepo;
    private final OperationTypeRepo opTypeRepo;
    private final LabwareRepo lwRepo;
    private final WorkService workService;
    private final OperationService opService;
    private final LabwareValidatorFactory lwValFactory;

    @Autowired
    public SolutionTransferServiceImp(SolutionRepo solutionRepo, OperationSolutionRepo opSolRepo,
                                      OperationTypeRepo opTypeRepo, LabwareRepo lwRepo,
                                      WorkService workService, OperationService opService,
                                      LabwareValidatorFactory lwValFactory) {
        this.solutionRepo = solutionRepo;
        this.opSolRepo = opSolRepo;
        this.opTypeRepo = opTypeRepo;
        this.lwRepo = lwRepo;
        this.workService = workService;
        this.opService = opService;
        this.lwValFactory = lwValFactory;
    }

    @Override
    public OperationResult perform(User user, SolutionTransferRequest request) throws ValidationException {
        requireNonNull(user, "User is null.");
        requireNonNull(request, "Request is null.");

        Collection<String> problems = new LinkedHashSet<>();
        Work work = workService.validateUsableWork(problems, request.getWorkNumber());
        UCMap<Labware> labware = loadLabware(problems, request.getLabware());
        UCMap<Solution> solutions = loadSolutions(problems, request.getLabware());

        if (!problems.isEmpty()) {
            throw new ValidationException("The request could not be validated.", problems);
        }
        return record(user, work, request.getLabware(), labware, solutions);
    }

    /**
     * Loads the labware, checks for problems.
     * @param problems receptacle for problems
     * @param stls the parts of the request specifying the labware barcodes
     * @return the labware, mapped from its barcode
     */
    public UCMap<Labware> loadLabware(Collection<String> problems, Collection<SolutionTransferLabware> stls) {
        if (stls.isEmpty()) {
            problems.add("No labware specified.");
            return new UCMap<>(0);
        }
        List<String> barcodes = new ArrayList<>();
        boolean anyNull = false;
        for (SolutionTransferLabware stl : stls) {
            String barcode = stl.getBarcode();
            if (nullOrEmpty(barcode)) {
                anyNull = true;
            } else {
                barcodes.add(barcode);
            }
        }
        if (anyNull) {
            problems.add("Labware barcode missing.");
        }
        if (barcodes.isEmpty()) {
            return new UCMap<>(0);
        }
        LabwareValidator val = lwValFactory.getValidator();
        val.loadLabware(lwRepo, barcodes);
        val.validateSources();
        problems.addAll(val.getErrors());
        return UCMap.from(val.getLabware(), Labware::getBarcode);
    }

    /**
     * Loads the solutions, checks for problems
     * @param problems receptacle for problems
     * @param stls the parts of the request specifying the solutions
     * @return the solution, mapped from their names
     */
    public UCMap<Solution> loadSolutions(Collection<String> problems, Collection<SolutionTransferLabware> stls) {
        Set<String> solutionNames = new HashSet<>(stls.size());
        boolean anyNull = false;
        for (SolutionTransferLabware stl : stls) {
            String solutionName = stl.getSolution();
            if (nullOrEmpty(solutionName)) {
                anyNull = true;
            } else {
                solutionNames.add(solutionName.toUpperCase());
            }
        }
        if (anyNull) {
            problems.add("Solution name missing.");
        }
        if (solutionNames.isEmpty()) {
            return new UCMap<>();
        }
        UCMap<Solution> solutionMap = UCMap.from(solutionRepo.findAllByNameIn(solutionNames), Solution::getName);

        List<String> missing = stls.stream()
                .map(SolutionTransferLabware::getSolution)
                .filter(s -> !nullOrEmpty(s) && solutionMap.get(s)==null)
                .filter(BasicUtils.distinctUCSerial())
                .map(BasicUtils::repr)
                .collect(toList());
        if (!missing.isEmpty()) {
            problems.add("Unknown solution: "+missing);
        }
        return solutionMap;
    }

    /**
     * Records the operations
     * @param user the user responsible
     * @param work the work to link to the operations
     * @param stls the specification of what to record
     * @param lwMap the map to look up labware
     * @param solutions the map to look up solutions
     * @return the operations and labware
     */
    public OperationResult record(User user, Work work, List<SolutionTransferLabware> stls,
                                  UCMap<Labware> lwMap, UCMap<Solution> solutions) {
        List<Operation> ops = recordOps(user, stls, lwMap, solutions);
        workService.link(work, ops);
        List<Labware> lwList = stls.stream()
                .map(stl -> lwMap.get(stl.getBarcode()))
                .collect(toList());
        return new OperationResult(ops, lwList);
    }

    /**
     * Creates ops and links them to the specified solutions
     * @param user user responsible
     * @param stls labware barcodes and solution names
     * @param lwMap map to look up labware
     * @param solutions map to look up solutions
     * @return the created operations
     */
    public List<Operation> recordOps(User user, List<SolutionTransferLabware> stls, UCMap<Labware> lwMap,
                                     UCMap<Solution> solutions) {
        OperationType opType = opTypeRepo.getByName("Solution transfer");
        return stls.stream()
                .map(stl -> recordOp(opType, user, lwMap.get(stl.getBarcode()), solutions.get(stl.getSolution())))
                .collect(toList());
    }

    /**
     * Creates the operation and links it to a solution
     * @param opType the operation type
     * @param user the user responsible
     * @param lw the labware for the operation
     * @param solution the solution for the operation
     * @return the newly created operation
     */
    public Operation recordOp(OperationType opType, User user, Labware lw, Solution solution) {
        Operation op = opService.createOperationInPlace(opType, user, lw, null, null);
        final Integer opId = op.getId();
        final Integer lwId = lw.getId();
        final Integer solutionId = solution.getId();
        List<OperationSolution> opSols = op.getActions().stream()
                .map(ac -> ac.getSample().getId())
                .distinct()
                .map(sampleId -> new OperationSolution(opId, solutionId, lwId, sampleId))
                .collect(toList());
        opSolRepo.saveAll(opSols);
        return op;
    }
}
