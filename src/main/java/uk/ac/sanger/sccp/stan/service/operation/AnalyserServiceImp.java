package uk.ac.sanger.sccp.stan.service.operation;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.AnalyserRequest;
import uk.ac.sanger.sccp.stan.request.AnalyserRequest.AnalyserLabware;
import uk.ac.sanger.sccp.stan.request.AnalyserRequest.SampleROI;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelper;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelperFactory;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * @author dr6
 */
@Service
public class AnalyserServiceImp extends BaseResultService implements AnalyserService {
    public static final String PROBE_HYBRIDISATION_NAME = CompletionServiceImp.PROBE_HYBRIDISATION_NAME;
    public static final String ANALYSER_OP_NAME = "Xenium analyser";

    public static final String LOT_A_NAME = "decoding reagent A lot", LOT_B_NAME = "decoding reagent B lot",
            RUN_NAME = "run", POSITION_NAME = "cassette position", CELL_SEGMENTATION_LOT_NAME = "cell segmentation lot",
            DECODING_CONSUMABLES_LOT_NAME = "decoding consumables lot";

    public static final String EQUIPMENT_CATEGORY = "xenium analyser";

    private final OperationService opService;
    private final WorkService workService;
    private final LabwareNoteRepo lwNoteRepo;
    private final RoiRepo roiRepo;
    private final Validator<String> decodingReagentLotValidator;
    private final Validator<String> runNameValidator;
    private final Validator<String> roiValidator;
    private final Validator<String> cellSegmentationLotValidator;
    private final Validator<String> decodingConsumablesLotValidator;
    private final ValidationHelperFactory valFactory;
    // validators

    @Autowired
    public AnalyserServiceImp(LabwareValidatorFactory lwValFactory, OpSearcher opSearcher,
                              OperationService opService, WorkService workService,
                              LabwareRepo lwRepo, OperationTypeRepo opTypeRepo, OperationRepo opRepo,
                              LabwareNoteRepo lwNoteRepo, RoiRepo roiRepo,
                              @Qualifier("decodingReagentLotValidator") Validator<String> decodingReagentLotValidator,
                              @Qualifier("runNameValidator") Validator<String> runNameValidator,
                              @Qualifier("roiValidator") Validator<String> roiValidator,
                              @Qualifier("cellSegmentationLotValidator") Validator<String> cellSegmentationLotValidator,
                              @Qualifier("decodingConsumablesLotValidator") Validator<String> decodingConsumablesLotValidator,
                              ValidationHelperFactory valFactory) {
        super(lwValFactory, opTypeRepo, opRepo, lwRepo, opSearcher);
        this.opService = opService;
        this.workService = workService;
        this.lwNoteRepo = lwNoteRepo;
        this.roiRepo = roiRepo;
        this.decodingReagentLotValidator = decodingReagentLotValidator;
        this.runNameValidator = runNameValidator;
        this.roiValidator = roiValidator;
        this.cellSegmentationLotValidator = cellSegmentationLotValidator;
        this.decodingConsumablesLotValidator = decodingConsumablesLotValidator;
        this.valFactory = valFactory;
    }

    @Override
    public OperationResult perform(User user, AnalyserRequest request) throws ValidationException {
        final Collection<String> problems = new LinkedHashSet<>();
        UCMap<Labware> lwMap = checkLabware(problems, request.getLabware());
        OperationType opType = checkOpType(problems, request.getOperationType());
        Map<Integer, Operation> priorOps = loadPriorOps(problems, opType, lwMap.values());
        if (request.getPerformed()!=null) {
            checkTimestamp(problems, request.getPerformed(), priorOps, lwMap.values());
        }
        UCMap<Work> workMap = loadWork(problems, request.getLabware());
        checkCassettePositions(problems, request.getLabware());
        checkRois(problems, request.getLabware());
        checkSamples(problems, request.getLabware(), lwMap);
        validateLot(problems, request.getLotNumberA());
        validateLot(problems, request.getLotNumberB());
        validateCellSegmentationLot(problems, request.getCellSegmentationLot());
        validateDecodingConsumablesLot(problems, request.getLabware());
        validateRunName(problems, request.getRunName());
        ValidationHelper val = valFactory.getHelper();
        Equipment equipment = val.checkEquipment(request.getEquipmentId(), EQUIPMENT_CATEGORY, true);
        problems.addAll(val.getProblems());

        if (!problems.isEmpty()) {
            throw new ValidationException(problems);
        }

        return record(user, request, opType, lwMap, workMap, equipment);
    }

    /**
     * Loads and validates the labware in the request
     * @param problems receptacle for problems
     * @param als requests labware
     * @return map of labware from its barcodes
     */
    public UCMap<Labware> checkLabware(Collection<String> problems, List<AnalyserLabware> als) {
        if (als.isEmpty()) {
            problems.add("No labware specified.");
            return new UCMap<>(0);
        }
        boolean anyMissing = false;
        List<String> barcodes = new ArrayList<>(als.size());
        for (AnalyserLabware al : als) {
            if (nullOrEmpty(al.getBarcode())) {
                anyMissing = true;
            } else {
                barcodes.add(al.getBarcode());
            }
        }
        if (anyMissing) {
            problems.add("Labware barcode missing.");
        }
        if (barcodes.isEmpty()) {
            return new UCMap<>(0);
        }
        return loadLabware(problems, barcodes);
    }

    /**
     * Loads and checks the specified op type
     * @param problems receptacle for problems
     * @param opName name of the operation type
     * @return the loaded operation type
     */
    public OperationType checkOpType(Collection<String> problems, String opName) {
        if (nullOrEmpty(opName)) {
            problems.add("No operation type specified.");
            return null;
        }
        OperationType opType = loadOpType(problems, opName);
        if (opType!=null && (!opType.inPlace() || precedingOpTypeName(opType)==null)) {
            problems.add("The specified operation type "+opType.getName()+" cannot be used in this operation.");
        }
        return opType;
    }

    /**
     * What is the name of the operation type required to precede the given op type?
     * @param opType the op type being performed
     * @return the name of the required preceding op type, or null
     */
    public String precedingOpTypeName(OperationType opType) {
        if (opType!=null && opType.getName().equalsIgnoreCase(ANALYSER_OP_NAME)) {
            return PROBE_HYBRIDISATION_NAME;
        }
        return null;
    }

    /**
     * Loads operations of whatever the op type is that must precede the specified op type.
     * @param problems receptacle for problems
     * @param opType the type of operation currently being performed
     * @param labware the labware being worked on
     * @return a map of labware id to the relevant prior operation, if found
     */
    public Map<Integer, Operation> loadPriorOps(Collection<String> problems, OperationType opType,
                                                Collection<Labware> labware) {
        String priorOpName = precedingOpTypeName(opType);
        if (priorOpName==null) {
            return Map.of();
        }
        OperationType priorOpType = opTypeRepo.findByName(priorOpName).orElse(null);
        if (priorOpType==null) {
            problems.add("Operation type "+priorOpName+" is missing from the database.");
            return Map.of();
        }
        return lookUpLatestOps(problems, priorOpType, labware, true);
    }

    /**
     * Checks the given timestamp when one is given
     * @param problems receptacle for problems
     * @param timestamp the given timestamp
     * @param priorOps the prior ops on the labware
     * @param labware the specified labware
     */
    public void checkTimestamp(Collection<String> problems, @NotNull LocalDateTime timestamp,
                               Map<Integer, Operation> priorOps, Collection<Labware> labware) {
        List<String> tooEarlyBarcodes = labware.stream()
                .map(lw -> {
                    Operation op = priorOps.get(lw.getId());
                    return (op!=null && op.getPerformed().isAfter(timestamp) ? lw.getBarcode() : null);
                }).filter(Objects::nonNull)
                .toList();
        if (!tooEarlyBarcodes.isEmpty()) {
            problems.add("The given date is before the preceding operation for labware " + tooEarlyBarcodes+".");
        }
    }

    /**
     * Loads the works specified in the request
     * @param problems receptacle for problems
     * @param als request including work numbers
     * @return map of work from work numbers
     */
    public UCMap<Work> loadWork(Collection<String> problems, List<AnalyserLabware> als) {
        List<String> workNumbers = als.stream().map(AnalyserLabware::getWorkNumber).collect(toList());
        return workService.validateUsableWorks(problems, workNumbers);
    }

    /**
     * Checks cassette positions are specified and unique.
     * @param problems receptacle for problems
     * @param als request including cassette positions
     */
    public void checkCassettePositions(Collection<String> problems, List<AnalyserLabware> als) {
        Set<CassettePosition> positions = EnumSet.noneOf(CassettePosition.class);
        Set<CassettePosition> repeated = EnumSet.noneOf(CassettePosition.class);
        boolean anyMissing = false;
        for (AnalyserLabware al : als) {
            if (al.getPosition()==null) {
                anyMissing = true;
            } else if (!positions.add(al.getPosition())) {
                repeated.add(al.getPosition());
            }
        }
        if (anyMissing) {
            problems.add("Cassette position not specified.");
        }
        if (!repeated.isEmpty()) {
            problems.add("Cassette position specified multiple times: "+repeated);
        }
    }

    /**
     * Checks ROIs (regions of interest) are appropriate
     * @param problems receptacle for problems
     * @param als the request including rois
     */
    public void checkRois(Collection<String> problems, List<AnalyserLabware> als) {
        Set<String> seenUC = new HashSet<>();
        boolean anyMissing = false;
        for (AnalyserLabware al : als) {
            for (SampleROI sr : al.getSamples()) {
                if (isBlank(sr.getRoi())) {
                    anyMissing = true;
                } else if (seenUC.add(sr.getRoi().toUpperCase())) {
                    roiValidator.validate(sr.getRoi(), problems::add);
                }
            }
        }
        if (anyMissing) {
            problems.add("ROI not specified.");
        }
    }

    /**
     * Checks samples and slots specified in the request
     * @param problems receptacle for problems
     * @param als the requests including slots and samples
     * @param lwMap map to up labware by barcode
     */
    public void checkSamples(Collection<String> problems, List<AnalyserLabware> als, UCMap<Labware> lwMap) {
        for (AnalyserLabware al : als) {
            Labware lw = lwMap.get(al.getBarcode());
            if (lw!=null) {
                checkLabwareSamples(problems, lw, al.getSamples());
            }
        }
    }

    /**
     * Checks the samples and slots specified for the given item of labware
     * @param problems receptacle for problems
     * @param lw labware
     * @param srs specified samples and slots
     */
    public void checkLabwareSamples(Collection<String> problems, Labware lw, List<SampleROI> srs) {
        if (srs.isEmpty()) {
            problems.add("No ROIs specified for labware "+lw.getBarcode()+".");
            return;
        }

        boolean anyAddressMissing = false;
        boolean anySampleIdMissing = false;
        Set<Address> nonExistentSlots = new LinkedHashSet<>();
        Set<AddressAndSampleId> nonPresentSamples = new LinkedHashSet<>();
        Set<AddressAndSampleId> seen = new HashSet<>(srs.size());
        Set<AddressAndSampleId> repeated = new LinkedHashSet<>();

        for (SampleROI sr : srs) {
            AddressAndSampleId aas;
            if (sr.getAddress()!=null && sr.getSampleId()!=null) {
                aas = new AddressAndSampleId(sr.getAddress(), sr.getSampleId());
                if (!seen.add(aas)) {
                    repeated.add(aas);
                }
            } else {
                aas = null;
            }
            Slot slot;
            if (sr.getAddress()==null) {
                anyAddressMissing = true;
                slot = null;
            } else {
                slot = lw.optSlot(sr.getAddress()).orElse(null);
                if (slot==null) {
                    nonExistentSlots.add(sr.getAddress());
                }
            }
            if (sr.getSampleId()==null) {
                anySampleIdMissing = true;
            } else if (slot!=null && slot.getSamples().stream().noneMatch(sam -> sam.getId().equals(sr.getSampleId()))) {
                nonPresentSamples.add(aas);
            }
        }

        if (anyAddressMissing) {
            problems.add("Address not specified in request.");
        }
        if (anySampleIdMissing) {
            problems.add("Sample id not specified in request.");
        }

        if (!nonExistentSlots.isEmpty()) {
            problems.add(String.format("No such slot%s in labware %s: %s", nonExistentSlots.size()==1 ? "" : "s",
                    lw.getBarcode(), nonExistentSlots));
        }
        if (!nonPresentSamples.isEmpty()) {
            problems.add("Sample id not present in specified slot of "+lw.getBarcode()+": "+nonPresentSamples);
        }
        if (!repeated.isEmpty()) {
            problems.add("Sample and slot specified multiple times for labware "+lw.getBarcode()+": "+repeated);
        }
    }

    /** Validates the decoding reagent lot number */
    public void validateLot(Collection<String> problems, String lot) {
        if (isBlank(lot)) {
            problems.add("Missing lot number.");
        } else {
            decodingReagentLotValidator.validate(lot, problems::add);
        }
    }

    /** Validates the cell segmentation lot number */
    public void validateCellSegmentationLot(Collection<String> problems, String lot) {
        if (!nullOrEmpty(lot)) {
            cellSegmentationLotValidator.validate(lot, problems::add);
        }
    }

    /** Sanitises and validates the decoding consumables lot number */
    public void validateDecodingConsumablesLot(Collection<String> problems, Collection<AnalyserLabware> als) {
        final Consumer<String> addProblem = problems::add;
        for (AnalyserLabware al : als) {
            String lot = al.getDecodingConsumablesLot();
            if (lot==null) {
                continue;
            }
            lot = emptyToNull(lot.trim());
            al.setDecodingConsumablesLot(lot);
            if (lot != null) {
                decodingConsumablesLotValidator.validate(lot, addProblem);
            }
        }
    }

    /** Validates the run name */
    public void validateRunName(Collection<String> problems, String runName) {
        if (isBlank(runName)) {
            problems.add("Missing run name.");
        } else if (runNameValidator.validate(runName, problems::add)) {
            if (lwNoteRepo.existsByNameAndValue(RUN_NAME, runName)) {
                problems.add("Run name already used: "+repr(runName));
            }
        }
    }

    /**
     * Records the operations for the given request.
     * Links to work, records ROIs and labware notes.
     * @param user the user responsible
     * @param request the operation request
     * @param opType the type of operation to record
     * @param lwMap the labware mapped from barcodes
     * @param equipment the equipment used in this operation
     * @param workMap the work mapped from work numbers
     * @return the labware and operations recorded
     */
    public OperationResult record(User user, AnalyserRequest request, OperationType opType,
                                  UCMap<Labware> lwMap, UCMap<Work> workMap, Equipment equipment) {
        String lotA = request.getLotNumberA().trim();
        String lotB = request.getLotNumberB().trim();
        String run = request.getRunName().trim();
        String cellSegmentationLot = nullOrEmpty(request.getCellSegmentationLot()) ? null : request.getCellSegmentationLot().trim();
        final int numLw = request.getLabware().size();
        List<Labware> labware = new ArrayList<>(numLw);
        List<Operation> ops = new ArrayList<>(numLw);
        UCMap<List<Operation>> workOps = new UCMap<>();

        List<LabwareNote> lwNotes = new ArrayList<>();
        List<Roi> rois = new ArrayList<>();
        Consumer<Operation> opModifier = op -> op.setEquipment(equipment);
        for (AnalyserLabware al : request.getLabware()) {
            Labware lw = lwMap.get(al.getBarcode());
            Work work = workMap.get(al.getWorkNumber());
            Operation op = opService.createOperationInPlace(opType, user, lw, null, opModifier);
            workOps.computeIfAbsent(work.getWorkNumber(), k -> new ArrayList<>(numLw)).add(op);
            lwNotes.add(new LabwareNote(null, lw.getId(), op.getId(), RUN_NAME, run));
            lwNotes.add(new LabwareNote(null, lw.getId(), op.getId(), LOT_A_NAME, lotA));
            lwNotes.add(new LabwareNote(null, lw.getId(), op.getId(), LOT_B_NAME, lotB));
            if (cellSegmentationLot != null) {
                lwNotes.add(new LabwareNote(null, lw.getId(), op.getId(), CELL_SEGMENTATION_LOT_NAME, cellSegmentationLot));
            }
            if (al.getDecodingConsumablesLot()!=null) {
                lwNotes.add(new LabwareNote(null, lw.getId(), op.getId(), DECODING_CONSUMABLES_LOT_NAME, al.getDecodingConsumablesLot()));
            }
            lwNotes.add(new LabwareNote(null, lw.getId(), op.getId(), POSITION_NAME, al.getPosition().toString()));
            addRois(rois, op.getId(), lw, al.getSamples());
            labware.add(lw);
            ops.add(op);
        }

        workOps.forEach((wn, wnOps) -> {
            Work work = workMap.get(wn);
            workService.link(work, wnOps);
        });

        lwNoteRepo.saveAll(lwNotes);
        roiRepo.saveAll(rois);
        if (request.getPerformed()!=null) {
            ops.forEach(op -> op.setPerformed(request.getPerformed()));
            opRepo.saveAll(ops);
        }

        return new OperationResult(ops, labware);
    }

    /**
     * Makes new (unsaved) ROIs as indicated in the request
     * @param rois receptacle for new ROIs
     * @param opId operation id
     * @param lw labware
     * @param srs specification of ROIs
     */
    public void addRois(Collection<Roi> rois, Integer opId, Labware lw, Collection<SampleROI> srs) {
        srs.stream()
                .map(sr -> new Roi(lw.getSlot(sr.getAddress()).getId(), sr.getSampleId(), opId, sr.getRoi()))
                .forEach(rois::add);
    }

    /** An address and a sample id, used for deduplication */
    record AddressAndSampleId(Address address, Integer sampleId) {
        @Override
        public String toString() {
            return String.format("{sampleId: %s, address: %s}", sampleId, address);
        }
    }
}
