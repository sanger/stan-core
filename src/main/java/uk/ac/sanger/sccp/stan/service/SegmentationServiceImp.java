package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.SegmentationRequest;
import uk.ac.sanger.sccp.stan.request.SegmentationRequest.PanelLot;
import uk.ac.sanger.sccp.stan.request.SegmentationRequest.SegmentationLabware;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelper;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelperFactory;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.stan.service.work.WorkService.WorkOp;
import uk.ac.sanger.sccp.utils.UCMap;

import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * @author dr6
 */
@Service
public class SegmentationServiceImp implements SegmentationService {
    public static final String CELL_SEGMENTATION_OP_NAME = "Cell segmentation",
            QC_OP_NAME = "Cell segmentation QC";

    private final Clock clock;
    private final ValidationHelperFactory valHelperFactory;

    private final OperationService opService;
    private final WorkService workService;

    private final OperationRepo opRepo;
    private final OperationTypeRepo opTypeRepo;
    private final OperationCommentRepo opComRepo;
    private final LabwareNoteRepo noteRepo;
    private final ProteinPanelRepo proteinPanelRepo;
    private final OpPanelRepo opPanelRepo;

    private final Validator<String> reagentLotValidator;
    private final Validator<String> panelLotValidator;

    public SegmentationServiceImp(Clock clock, ValidationHelperFactory valHelperFactory,
                                  OperationService opService, WorkService workService,
                                  OperationRepo opRepo, OperationTypeRepo opTypeRepo,
                                  OperationCommentRepo opComRepo, LabwareNoteRepo noteRepo,
                                  ProteinPanelRepo proteinPanelRepo, OpPanelRepo opPanelRepo,
                                  @Qualifier("reagentLotValidator") Validator<String> reagentLotValidator,
                                  @Qualifier("panelLotValidator") Validator<String> panelLotValidator) {
        this.clock = clock;
        this.valHelperFactory = valHelperFactory;
        this.opService = opService;
        this.workService = workService;
        this.opRepo = opRepo;
        this.opTypeRepo = opTypeRepo;
        this.opComRepo = opComRepo;
        this.noteRepo = noteRepo;
        this.proteinPanelRepo = proteinPanelRepo;
        this.opPanelRepo = opPanelRepo;
        this.reagentLotValidator = reagentLotValidator;
        this.panelLotValidator = panelLotValidator;
    }

    @Override
    public OperationResult perform(User user, SegmentationRequest request) throws ValidationException {
        SegmentationData data = validate(user, request);
        if (!data.problems.isEmpty()) {
            throw new ValidationException(data.problems);
        }
        return record(request.getLabware(), user, data);
    }

    /**
     * Validates the request and loads information into the returned structure
     * @param user the user responsible for the request
     * @param request the details of the request
     * @return the data loaded during validation
     */
    SegmentationData validate(User user, SegmentationRequest request) {
        ValidationHelper val = valHelperFactory.getHelper();
        Set<String> problems = val.getProblems();
        SegmentationData data = new SegmentationData(problems);
        if (user==null) {
            problems.add("No user supplied.");
        }
        if (request==null) {
            problems.add("No request supplied.");
            return data;
        }
        Set<String> opNamesUC = Stream.of(CELL_SEGMENTATION_OP_NAME, QC_OP_NAME).map(String::toUpperCase).collect(toSet());
        data.opType = val.checkOpType(request.getOperationType(), EnumSet.of(OperationTypeFlag.IN_PLACE),
                null, ot -> opNamesUC.contains(ot.getName().toUpperCase()));
        if (nullOrEmpty(request.getLabware())) {
            problems.add("No labware specified.");
            return data;
        }
        data.labware = loadLabware(val, request.getLabware());
        data.works = loadWorks(val, request.getLabware());
        data.comments = loadComments(val, request.getLabware());
        data.panels = loadPanels();
        checkCostings(problems, data.opType, request.getLabware());
        UCMap<LocalDateTime> priorOpTimes = checkPriorOps(problems, data.opType, data.labware.values());
        checkTimestamps(val, clock, request.getLabware(), data.labware, priorOpTimes);
        checkReagentLots(problems, request.getLabware());
        checkProteinPanels(problems, data.panels, request.getLabware());
        return data;
    }

    /**
     * Loads and checks the labware
     * @param val validation helper
     * @param lwReqs details of the request
     * @return the loaded labware, mapped from barcodes
     */
    UCMap<Labware> loadLabware(ValidationHelper val, List<SegmentationLabware> lwReqs) {
        List<String> barcodes = lwReqs.stream()
                .map(SegmentationLabware::getBarcode)
                .toList();
        return val.checkLabware(barcodes);
    }

    /**
     * Loads and checks the works
     * @param val validation helper
     * @param lwReqs details of the request
     * @return the loaded works, mapped from work number
     */
    UCMap<Work> loadWorks(ValidationHelper val, List<SegmentationLabware> lwReqs) {
        List<String> workNumbers = lwReqs.stream()
                .map(SegmentationLabware::getWorkNumber)
                .toList();
        return val.checkWork(workNumbers);
    }

    /**
     * Loads and checks the comments
     * @param val validation helper
     * @param lwReqs details of the request
     * @return the loaded comments, mapped from ids
     */
    Map<Integer, Comment> loadComments(ValidationHelper val, List<SegmentationLabware> lwReqs) {
        Stream<Integer> commentIds = lwReqs.stream()
                .filter(lwReq -> lwReq.getCommentIds()!=null)
                .flatMap(lwReq -> lwReq.getCommentIds().stream());
        return val.checkCommentIds(commentIds);
    }

    /** Loads all protein panels */
    UCMap<ProteinPanel> loadPanels() {
        return stream(proteinPanelRepo.findAll()).collect(UCMap.toUCMap(ProteinPanel::getName));
    }

    /**
     * Checks if the labware has the required prior operations, if appropriate
     * @param problems receptacle for problems
     * @param opType the operation type, if known
     * @param labware the labware involved
     * @return the prior op timestamp for each labware, if found. Null if no prior op is required
     */
    UCMap<LocalDateTime> checkPriorOps(Collection<String> problems, OperationType opType, Collection<Labware> labware) {
        if (opType==null || !opType.getName().equalsIgnoreCase(QC_OP_NAME) || nullOrEmpty(labware)) {
            return null;
        }
        Map<Integer, Labware> idLabware = labware.stream().collect(inMap(Labware::getId));
        OperationType priorOpType = opTypeRepo.getByName(CELL_SEGMENTATION_OP_NAME);
        List<Operation> priorOps = opRepo.findAllByOperationTypeAndDestinationLabwareIdIn(priorOpType, idLabware.keySet());
        UCMap<LocalDateTime> opTimes = new UCMap<>(priorOps.size());
        for (Operation op : priorOps) {
            Set<Integer> opLwIds = op.getActions().stream()
                    .map(a -> a.getDestination().getLabwareId())
                    .collect(toSet());
            for (Integer lwId : opLwIds) {
                Labware lw = idLabware.get(lwId);
                if (lw!=null && greater(op.getPerformed(), opTimes.get(lw.getBarcode()))) {
                    opTimes.put(lw.getBarcode(), op.getPerformed());
                }
            }
        }
        List<String> missing = labware.stream()
                .map(Labware::getBarcode)
                .filter(bc -> opTimes.get(bc)==null)
                .toList();
        if (!missing.isEmpty()) {
            problems.add(priorOpType.getName()+" has not been recorded on labware "+missing+".");
        }
        return opTimes;
    }

    /**
     * Is a greater than b? regarding null as less than everything
     * @param a thing that might be greater
     * @param b thing that might be lesser
     * @return true if a is non-null and b is null or a is greater than b
     * @param <E> the type of thing being compared
     */
    static <E extends Comparable<E>> boolean greater(E a, E b) {
        return (a!=null && (b==null || a.compareTo(b) > 0));
    }

    /**
     * Checks the costings
     * @param problems receptacle for problems found
     * @param lwReqs details of the request
     */
    void checkCostings(Collection<String> problems, OperationType opType, List<SegmentationLabware> lwReqs) {
        if (opType==null) {
            return;
        }
        if (opType.getName().equalsIgnoreCase(CELL_SEGMENTATION_OP_NAME)) {
            if (lwReqs.stream().anyMatch(lwReq -> lwReq.getCosting() == null)) {
                problems.add("Costing missing from request.");
            }
        } else if (opType.getName().equalsIgnoreCase(QC_OP_NAME)) {
            if (lwReqs.stream().anyMatch(lwReq -> lwReq.getCosting()!=null)) {
                problems.add("Costing not expected in this request.");
            }
        }
    }

    /**
     * Checks the timestamps
     * @param val validation helper
     * @param clock to get current time
     * @param lwReqs details of the request
     * @param priorOpTimes times the prior op was recorded on each labware, if appropriate
     * @param lwMap map to look up labware from its barcode
     */
    void checkTimestamps(ValidationHelper val, Clock clock, List<SegmentationLabware> lwReqs, UCMap<Labware> lwMap,
                         UCMap<LocalDateTime> priorOpTimes) {
        LocalDate today = LocalDate.now(clock);
        for (SegmentationLabware lwReq : lwReqs) {
            if (lwReq.getPerformed()!=null) {
                String barcode = lwReq.getBarcode();
                val.checkTimestamp(lwReq.getPerformed(), today, lwMap==null ? null : lwMap.get(barcode),
                        priorOpTimes==null ? null : priorOpTimes.get(barcode));
            }
        }
    }

    /**
     * Checks the reagent lots
     * @param problems receptacle for problems
     * @param lwReqs details of the request
     */
    void checkReagentLots(final Collection<String> problems, final Collection<SegmentationLabware> lwReqs) {
        final Consumer<String> problemAdd = problems::add;
        for (SegmentationLabware lwReq : lwReqs) {
            String lot = lwReq.getReagentLot();
            if (lot != null) {
                lot = emptyToNull(lot.trim());
                lwReq.setReagentLot(lot);
            }
            if (lot != null) {
                reagentLotValidator.validate(lot, problemAdd);
            }
        }
    }

    /**
     * Checks for problems with protein panels and their lots.
     * @param problems receptacle for problems
     * @param panels known panels
     * @param sls labware specs from request
     */
    void checkProteinPanels(final Collection<String> problems, UCMap<ProteinPanel> panels, final Collection<SegmentationLabware> sls) {
        Set<String> invalidNames = new LinkedHashSet<>();
        Set<String> repeatedNames = new LinkedHashSet<>();
        boolean anyMissingNames = false;
        boolean anyMissingLots = false;
        boolean anyMissingCosting = false;
        final Consumer<String> problemAdd = problems::add;
        for (SegmentationLabware sl : sls) {
            if (!nullOrEmpty(sl.getProteinPanels())) {
                Set<Integer> panelIdSet = new HashSet<>();
                for (PanelLot pl : sl.getProteinPanels()) {
                    if (nullOrEmpty(pl.getName())) {
                        anyMissingNames = true;
                    } else {
                        ProteinPanel pp = panels.get(pl.getName());
                        if (pp==null) {
                            invalidNames.add(repr(pl.getName()));
                        } else if (!panelIdSet.add(pp.getId())) {
                            repeatedNames.add(pp.getName());
                        }
                    }
                    if (nullOrEmpty(pl.getLot())) {
                        anyMissingLots = true;
                    } else {
                        panelLotValidator.validate(pl.getLot(), problemAdd);
                    }
                    if (pl.getCosting()==null) {
                        anyMissingCosting = true;
                    }
                }
            }
        }
        if (anyMissingNames) {
            problems.add("Protein panel name not specified.");
        }
        if (!invalidNames.isEmpty()) {
            problems.add("Unknown protein panel name: "+invalidNames);
        }
        if (!repeatedNames.isEmpty()) {
            problems.add("Protein panel given multiple times for the same labware: "+repeatedNames);
        }
        if (anyMissingLots) {
            problems.add("Protein panel lot not specified.");
        }
        if (anyMissingCosting) {
            problems.add("Protein panel costing not specified.");
        }
    }

    /**
     * Records the operations and all associated information for the request
     * @param lwReqs details of the request
     * @param user the user responsible
     * @param data data loaded during validation
     * @return the labware involved and operations created
     */
    OperationResult record(List<SegmentationLabware> lwReqs, User user, SegmentationData data) {
        List<Operation> ops = new ArrayList<>(lwReqs.size());
        List<Labware> labware = new ArrayList<>(lwReqs.size());
        List<WorkOp> newWorkOps = new ArrayList<>(lwReqs.size());
        List<Operation> opsToUpdate = new ArrayList<>();
        List<LabwareNote> newNotes = new ArrayList<>();
        List<OperationComment> newOpComs = new ArrayList<>();
        List<OpPanel> newOpPanels = new ArrayList<>();
        for (SegmentationLabware lwReq: lwReqs) {
            Labware lw = data.labware.get(lwReq.getBarcode());
            Operation op = recordOp(user, data, lwReq, lw, opsToUpdate, newNotes, newOpComs, newWorkOps, newOpPanels);
            labware.add(lw);
            ops.add(op);
        }
        if (!opsToUpdate.isEmpty()) {
            opRepo.saveAll(opsToUpdate);
        }
        if (!newNotes.isEmpty()) {
            noteRepo.saveAll(newNotes);
        }
        if (!newOpComs.isEmpty()) {
            opComRepo.saveAll(newOpComs);
        }
        if (!newOpPanels.isEmpty()) {
            opPanelRepo.saveAll(newOpPanels);
        }
        if (!newWorkOps.isEmpty()) {
            workService.linkWorkOps(newWorkOps.stream());
        }

        return new OperationResult(ops, labware);
    }

    /**
     * Records an operation and updates various lists with information to be saved
     * @param user the user responsible
     * @param data data loaded during validation
     * @param lwReq details of the request for this labware
     * @param lw the labware being operated on
     * @param opsToUpdate receptacle for ops that need to be updated
     * @param newNotes receptacle for new notes that need to be saved
     * @param newOpComs receptacle for new operation comments that need to be saved
     * @param newWorkOps receptacle for new work-operation links that need to be saved
     * @param newOpPanels receptacle for new op panels that need to be saved
     * @return the operation created
     */
    Operation recordOp(User user, SegmentationData data, SegmentationLabware lwReq, Labware lw,
                       final List<Operation> opsToUpdate, final List<LabwareNote> newNotes,
                       final List<OperationComment> newOpComs, final List<WorkOp> newWorkOps,
                       final List<OpPanel> newOpPanels) {
        final Operation op = opService.createOperationInPlace(data.opType, user, lw, null, null);
        if (lwReq.getPerformed() != null) {
            op.setPerformed(lwReq.getPerformed());
            opsToUpdate.add(op);
        }
        if (lwReq.getCosting()!=null) {
            newNotes.add(new LabwareNote(null, lw.getId(), op.getId(), "costing",
                    lwReq.getCosting().name()));
        }
        if (lwReq.getReagentLot()!=null) {
            newNotes.add(new LabwareNote(null, lw.getId(), op.getId(), "reagent lot", lwReq.getReagentLot()));
        }
        if (!lwReq.getCommentIds().isEmpty()) {
            lwReq.getCommentIds().stream()
                    .map(data.comments::get)
                    .flatMap(com -> op.getActions().stream()
                            .map(ac -> new OperationComment(null, com, op.getId(), ac.getSample().getId(),
                                    ac.getDestination().getId(), null)))
                    .forEach(newOpComs::add);
        }
        if (lwReq.getWorkNumber()!=null) {
            newWorkOps.add(new WorkOp(data.works.get(lwReq.getWorkNumber()), op));
        }
        if (!lwReq.getProteinPanels().isEmpty()) {
            lwReq.getProteinPanels().stream()
                    .map(pp -> new OpPanel(data.panels.get(pp.getName()), op.getId(), lw.getId(), pp.getLot(), pp.getCosting()))
                    .forEach(newOpPanels::add);
        }

        return op;
    }

    /** Data loaded during validation */
    static class SegmentationData {
        Collection<String> problems;
        OperationType opType;
        UCMap<Work> works;
        UCMap<Labware> labware;
        Map<Integer, Comment> comments;
        UCMap<ProteinPanel> panels;

        public SegmentationData(Collection<String> problems) {
            this.problems = problems;
        }
    }
}
