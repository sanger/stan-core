package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.stain.*;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * @author dr6
 */
@Service
public class ComplexStainServiceImp implements ComplexStainService {
    public static final String STAIN_RNASCOPE = "RNAscope", STAIN_IHC = "IHC";
    public static final String LW_NOTE_PLEX = "Plex", LW_NOTE_PANEL = "Panel",
            LW_NOTE_BOND_BARCODE = "Bond barcode", LW_NOTE_BOND_RUN = "Bond run";

    private final Pattern BOND_BARCODE_PTN = Pattern.compile("^[0-9A-Z]{8}$");

    private final WorkService workService;
    private final OperationService opService;

    private final LabwareValidatorFactory lwValFactory;

    private final OperationTypeRepo opTypeRepo;
    private final StainTypeRepo stainTypeRepo;
    private final LabwareRepo lwRepo;
    private final LabwareNoteRepo lwNoteRepo;

    @Autowired
    public ComplexStainServiceImp(WorkService workService, OperationService opService,
                                  LabwareValidatorFactory lwValFactory,
                                  OperationTypeRepo opTypeRepo, StainTypeRepo stainTypeRepo, LabwareRepo lwRepo,
                                  LabwareNoteRepo lwNoteRepo) {
        this.workService = workService;
        this.opService = opService;
        this.lwValFactory = lwValFactory;
        this.opTypeRepo = opTypeRepo;
        this.stainTypeRepo = stainTypeRepo;
        this.lwRepo = lwRepo;
        this.lwNoteRepo = lwNoteRepo;
    }

    @Override
    public OperationResult perform(User user, ComplexStainRequest request) throws ValidationException {
        requireNonNull(user, "User is null.");
        requireNonNull(request, "Request is null.");

        final Set<String> problems = new LinkedHashSet<>();

        OperationType opType = loadStainOpType(problems);
        UCMap<Labware> labwareMap = loadLabware(problems, request.getLabware());
        StainType stainType = loadStainType(problems, request.getStainType());
        validatePanel(problems, request.getPanel());
        validatePlex(problems, request.getPlex());
        UCMap<Work> workMap = loadWorks(problems, request.getLabware());
        validateBondRuns(problems, request.getLabware());
        validateBondBarcodes(problems, request.getLabware());

        if (!problems.isEmpty()) {
            throw new ValidationException("The request could not be validated.", problems);
        }

        return record(user, request, opType, stainType, labwareMap, workMap);
    }

    /**
     * Checks a valid panel is specified
     * @param problems receptacle for problems found
     * @param panel the specified panel
     */
    public void validatePanel(Collection<String> problems, StainPanel panel) {
        if (panel==null) {
            problems.add("No experiment panel specified.");
        }
    }

    /**
     * Checks a valid plex number is specified
     * @param problems receptacle for problems found
     * @param plex the specified plex number
     */
    public void validatePlex(Collection<String> problems, int plex) {
        if (plex < 1 || plex > 100) {
            problems.add("The plex number ("+plex+") should be in the range 1-100.");
        }
    }

    /**
     * Loads the works specified and checks they are usable
     * @param problems receptacle for problems found
     * @param csls the parts of the request specifying work numbers
     * @return a map of work from their work numbers
     */
    public UCMap<Work> loadWorks(Collection<String> problems, Collection<ComplexStainLabware> csls) {
        Set<String> workNumbers = csls.stream()
                .map(ComplexStainLabware::getWorkNumber)
                .filter(Objects::nonNull)
                .collect(toSet());
        return workService.validateUsableWorks(problems, workNumbers);
    }

    /**
     * Checks the bond runs are valid
     * @param problems receptacle for problems found
     * @param csls the parts of the request specifying bond numbers
     */
    public void validateBondRuns(Collection<String> problems, Collection<ComplexStainLabware> csls) {
        Set<Integer> invalidBondRuns = csls.stream()
                .map(ComplexStainLabware::getBondRun)
                .filter(run -> (run < 1 || run > 9999))
                .collect(BasicUtils.toLinkedHashSet());
        if (!invalidBondRuns.isEmpty()) {
            problems.add("Bond runs are expected to be in the range 1-9999: "+invalidBondRuns);
        }
    }

    /**
     * Checks the bond barcodes are valid
     * @param problems receptacle for problems found
     * @param csls the parts of the request specifying bond barcodes
     */
    public void validateBondBarcodes(Collection<String> problems, Collection<ComplexStainLabware> csls) {
        Set<String> invalidBondBarcodes = csls.stream()
                .map(ComplexStainLabware::getBondBarcode)
                .filter(bc -> bc==null || !BOND_BARCODE_PTN.matcher(bc).matches())
                .collect(BasicUtils.toLinkedHashSet());
        if (!invalidBondBarcodes.isEmpty()) {
            problems.add("Bond barcodes not of the expected format: "+invalidBondBarcodes);
        }
    }

    /**
     * Loads and checks the labware
     * @param problems receptacle for problems found
     * @param csls the parts of the request specifying the labware barcodes
     * @return the loaded labware, mapped from barcodes
     */
    public UCMap<Labware> loadLabware(Collection<String> problems, Collection<ComplexStainLabware> csls) {
        if (csls.isEmpty()) {
            problems.add("No labware specified.");
            return new UCMap<>();
        }
        List<String> barcodes = csls.stream().map(ComplexStainLabware::getBarcode).collect(toList());
        LabwareValidator val = lwValFactory.getValidator();
        val.loadLabware(lwRepo, barcodes);
        val.setUniqueRequired(true);
        val.validateSources();
        problems.addAll(val.getErrors());
        return UCMap.from(val.getLabware(), Labware::getBarcode);
    }

    /**
     * Loads the stain operation type and checks it is usable
     * @param problems receptacle for problems found
     * @return the stain op type
     */
    public OperationType loadStainOpType(Collection<String> problems) {
        var optOpType = opTypeRepo.findByName("Stain");
        if (optOpType.isEmpty()) {
            problems.add("Stain operation type not found.");
            return null;
        }
        OperationType opType = optOpType.get();
        if (!opType.inPlace()) {
            problems.add("Stain operation type cannot be recorded in place.");
        }
        if (!opType.has(OperationTypeFlag.STAIN)) {
            problems.add("Stain operation type does not have the stain flag.");
        }
        return opType;
    }

    /**
     * Loads the stain type and checks it is as expected
     * @param problems receptacle for problems found
     * @param stainName the name of the stain type
     * @return the loaded stain type
     */
    public StainType loadStainType(Collection<String> problems, String stainName) {
        if (stainName==null || stainName.isEmpty()) {
            problems.add("No stain type specified.");
            return null;
        }
        var optStainType = stainTypeRepo.findByName(stainName);
        if (optStainType.isEmpty()) {
            problems.add("Unknown stain type: "+repr(stainName));
            return null;
        }
        StainType stainType = optStainType.get();
        if (!stainType.getName().equalsIgnoreCase(STAIN_IHC)
                && !stainType.getName().equalsIgnoreCase(STAIN_RNASCOPE)) {
            problems.add("The stain type "+stainType.getName()+" was not expected for this type of request.");
        }
        return stainType;
    }

    /**
     * Records the operations and associated information as specified in the request
     * @param user the user responsible for the request
     * @param request the request of what to record
     * @param opType the operation type to record
     * @param stainType the stain type of the operation
     * @param labwareMap the labware involved, mapped from its barcode
     * @param workMap the works involved, mapped from its work number
     * @return the operations created and their labware
     */
    public OperationResult record(User user, ComplexStainRequest request, OperationType opType, StainType stainType,
                                  UCMap<Labware> labwareMap, UCMap<Work> workMap) {
        List<Labware> lwList = new ArrayList<>(request.getLabware().size());
        List<Operation> opList = new ArrayList<>(request.getLabware().size());
        UCMap<List<Operation>> workOps = new UCMap<>(request.getLabware().size());
        for (ComplexStainLabware csl : request.getLabware()) {
            Labware lw = labwareMap.get(csl.getBarcode());
            Operation op = createOp(user, lw, opType, stainType);
            recordLabwareNotes(request, csl, lw.getId(), op.getId());
            lwList.add(lw);
            opList.add(op);
            if (workMap.get(csl.getWorkNumber()) != null) {
                workOps.computeIfAbsent(csl.getWorkNumber(), k -> new ArrayList<>()).add(op);
            }
        }
        for (var entry : workOps.entrySet()) {
            workService.link(workMap.get(entry.getKey()), entry.getValue());
        }
        return new OperationResult(opList, lwList);
    }

    /**
     * Creates the operation in place with the given op type, user, labware, stain type.
     * @param user the user responsible for the operation
     * @param lw the labware associated with the operation
     * @param opType the op type of the operation
     * @param stainType the stain type of the operation
     * @return the created operation
     */
    public Operation createOp(User user, Labware lw, OperationType opType, StainType stainType) {
        return opService.createOperationInPlace(opType, user, lw, null, o -> o.setStainType(stainType));
    }

    /**
     * Records the information about the labware specified as labware notes
     * @param request the stain request
     * @param csl the request pertaining to this specific labware
     * @param lwId the id of the labware
     * @param opId the id of the operation
     */
    public void recordLabwareNotes(ComplexStainRequest request, ComplexStainLabware csl, Integer lwId, Integer opId) {
        List<LabwareNote> notes = List.of(
                new LabwareNote(null, lwId, opId, LW_NOTE_PLEX, String.valueOf(request.getPlex())),
                new LabwareNote(null, lwId, opId, LW_NOTE_PANEL, request.getPanel().name()),
                new LabwareNote(null, lwId, opId, LW_NOTE_BOND_BARCODE, csl.getBondBarcode()),
                new LabwareNote(null, lwId, opId, LW_NOTE_BOND_RUN, String.valueOf(csl.getBondRun()))
        );
        lwNoteRepo.saveAll(notes);
    }
}
