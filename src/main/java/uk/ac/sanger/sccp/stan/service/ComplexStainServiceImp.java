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
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.reprCollection;

/**
 * @author dr6
 */
@Service
public class ComplexStainServiceImp implements ComplexStainService {
    public static final String STAIN_RNASCOPE = "RNAscope", STAIN_IHC = "IHC";

    public static final String LW_NOTE_PLEX_RNASCOPE = "RNAscope plex",
            LW_NOTE_PLEX_IHC = "IHC plex";
    public static final String LW_NOTE_PANEL = "Panel",
            LW_NOTE_BOND_BARCODE = "Bond barcode", LW_NOTE_BOND_RUN = "Bond run";

    private final Pattern BOND_BARCODE_PTN = Pattern.compile("^[0-9A-Z]{4,8}$");

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
        List<StainType> stainTypes = loadStainTypes(problems, request.getStainTypes());
        UCMap<Work> workMap = loadWorks(problems, request.getLabware());
        validateBondRuns(problems, request.getLabware());
        validateBondBarcodes(problems, request.getLabware());
        validatePanels(problems, request.getLabware());
        validatePlexes(problems, stainTypes, request.getLabware());

        if (!problems.isEmpty()) {
            throw new ValidationException("The request could not be validated.", problems);
        }

        return record(user, request, opType, stainTypes, labwareMap, workMap);
    }

    /**
     * Checks the panels specified for each labware
     * @param problems receptacle for problems found
     * @param csls the labware parts of the request
     */
    public void validatePanels(Collection<String> problems, Collection<ComplexStainLabware> csls) {
        if (csls.stream().anyMatch(csl -> csl.getPanel()==null)) {
            problems.add("Experiment panel must be specified for each labware.");
        }
    }

    public void validatePlexes(Collection<String> problems, List<StainType> stainTypes,
                               Collection<ComplexStainLabware> csls) {
        boolean gotIhc = false;
        boolean gotRna = false;
        for (StainType st : stainTypes) {
            if (st.getName().equalsIgnoreCase(STAIN_RNASCOPE)) {
                gotRna = true;
            } else if (st.getName().equalsIgnoreCase(STAIN_IHC)) {
                gotIhc = true;
            }
        }
        boolean ihcError = false;
        boolean rnaError = false;
        boolean invalidRange = false;
        for (var csl : csls) {
            if (gotIhc ^ (csl.getPlexIHC()!=null)) {
                ihcError = true;
            }
            if (gotRna ^ (csl.getPlexRNAscope()!=null)) {
                rnaError = true;
            }
            for (Integer plex : new Integer[] { csl.getPlexIHC(), csl.getPlexRNAscope()}) {
                if (plex != null && (plex < 1 || plex > 100)) {
                    invalidRange = true;
                    break;
                }
            }
        }
        if (ihcError) {
            problems.add(gotIhc ? "IHC plex number is required for IHC stain." :
                    "IHC plex number is not expected for non-IHC stain.");
        }
        if (rnaError) {
            problems.add(gotRna ? "RNAscope plex number is required for RNAscope stain." :
                    "RNAscope plex number is not expected for non-RNAscope stain.");
        }
        if (invalidRange) {
            problems.add("Plex number is expected to be in the range 1 to 100.");
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
     * Loads the stain types and checks they are as expected
     * @param problems receptacle for problems found
     * @param stainNames the names of the stain types
     * @return the loaded stain types
     */
    public List<StainType> loadStainTypes(Collection<String> problems, Collection<String> stainNames) {
        if (stainNames==null || stainNames.isEmpty()) {
            problems.add("No stain types specified.");
            return List.of();
        }
        List<StainType> stainTypes = stainTypeRepo.findAllByNameIn(stainNames);
        if (stainTypes.size() < stainNames.size()) {
            UCMap<StainType> stainTypeMap = UCMap.from(stainTypes, StainType::getName);
            Set<String> seen = new HashSet<>(stainNames.size());
            Set<String> notFound = new LinkedHashSet<>();
            Set<String> repeated = new LinkedHashSet<>();
            for (String name : stainNames) {
                String nameUpper = (name==null ? null : name.toUpperCase());
                if (!seen.add(nameUpper)) {
                    repeated.add(name);
                } else if (stainTypeMap.get(name)==null) {
                    notFound.add(name);
                }
            }
            if (notFound.isEmpty() && repeated.isEmpty()) {
                problems.add("Couldn't load all specified stain types.");
            }
            if (!notFound.isEmpty()) {
                problems.add("Unknown stain type: "+reprCollection(notFound));
            }
            if (!repeated.isEmpty()) {
                problems.add("Repeated stain type: "+reprCollection(repeated));
            }
        }
        if (!stainTypes.isEmpty()) {
            Set<StainType> unexpected = stainTypes.stream()
                    .filter(st -> !st.getName().equalsIgnoreCase(STAIN_IHC)
                            && !st.getName().equalsIgnoreCase(STAIN_RNASCOPE))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!unexpected.isEmpty()) {
                problems.add("The supplied stain type was not expected for this request: " + unexpected);
            }
        }
        return stainTypes;
    }

    /**
     * Records the operations and associated information as specified in the request
     * @param user the user responsible for the request
     * @param request the request of what to record
     * @param opType the operation type to record
     * @param stainTypes the stain types of the operation
     * @param labwareMap the labware involved, mapped from its barcode
     * @param workMap the works involved, mapped from its work number
     * @return the operations created and their labware
     */
    public OperationResult record(User user, ComplexStainRequest request, OperationType opType, List<StainType> stainTypes,
                                  UCMap<Labware> labwareMap, UCMap<Work> workMap) {
        List<Labware> lwList = new ArrayList<>(request.getLabware().size());
        List<Operation> opList = new ArrayList<>(request.getLabware().size());
        UCMap<List<Operation>> workOps = new UCMap<>(request.getLabware().size());
        for (ComplexStainLabware csl : request.getLabware()) {
            Labware lw = labwareMap.get(csl.getBarcode());
            Operation op = createOp(user, lw, opType, stainTypes);
            recordLabwareNotes(csl, lw.getId(), op.getId());
            lwList.add(lw);
            opList.add(op);
            workOps.computeIfAbsent(csl.getWorkNumber(), k -> new ArrayList<>()).add(op);
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
     * @param stainTypes the stain types of the operation
     * @return the created operation
     */
    public Operation createOp(User user, Labware lw, OperationType opType, Collection<StainType> stainTypes) {
        Operation op = opService.createOperationInPlace(opType, user, lw, null, null);
        stainTypeRepo.saveOperationStainTypes(op.getId(), stainTypes);
        return op;
    }

    /**
     * Records the information about the labware specified as labware notes
     * @param csl the request pertaining to this specific labware
     * @param lwId the id of the labware
     * @param opId the id of the operation
     */
    public void recordLabwareNotes(ComplexStainLabware csl, Integer lwId, Integer opId) {
        List<LabwareNote> notes = new ArrayList<>(5);
        if (csl.getPlexIHC()!=null) {
            notes.add(new LabwareNote(null, lwId, opId, LW_NOTE_PLEX_IHC, String.valueOf(csl.getPlexIHC())));
        }
        if (csl.getPlexRNAscope()!=null) {
            notes.add(new LabwareNote(null, lwId, opId, LW_NOTE_PLEX_RNASCOPE, String.valueOf(csl.getPlexRNAscope())));
        }
        notes.add(new LabwareNote(null, lwId, opId, LW_NOTE_PANEL, csl.getPanel().name()));
        notes.add(new LabwareNote(null, lwId, opId, LW_NOTE_BOND_BARCODE, csl.getBondBarcode()));
        notes.add(new LabwareNote(null, lwId, opId, LW_NOTE_BOND_RUN, String.valueOf(csl.getBondRun())));
        lwNoteRepo.saveAll(notes);
    }
}
