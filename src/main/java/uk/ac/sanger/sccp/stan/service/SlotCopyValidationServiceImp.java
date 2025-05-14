package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest;
import uk.ac.sanger.sccp.stan.request.SlotCopyRequest.*;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelper;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelperFactory;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.stan.service.SlotCopyServiceImp.CYTASSIST_OP;
import static uk.ac.sanger.sccp.stan.service.SlotCopyServiceImp.VALID_BS_UPPER;
import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * @author dr6
 */
@Service
public class SlotCopyValidationServiceImp implements SlotCopyValidationService {
    private final Pattern LP_NUMBER_PTN = Pattern.compile("^(?:LP)?\\d{1,8}$", Pattern.CASE_INSENSITIVE);
    private final LabwareTypeRepo lwTypeRepo;
    private final LabwareRepo lwRepo;
    private final BioStateRepo bsRepo;
    private final ValidationHelperFactory valHelperFactory;
    private final Validator<String> preBarcodeValidator;
    private final Validator<String> lotNumberValidator;
    private final CleanedOutSlotService cleanedOutSlotService;

    @Autowired
    public SlotCopyValidationServiceImp(LabwareTypeRepo lwTypeRepo, LabwareRepo lwRepo, BioStateRepo bsRepo,
                                        ValidationHelperFactory valHelperFactory,
                                        @Qualifier("cytAssistBarcodeValidator") Validator<String> preBarcodeValidator,
                                        @Qualifier("lotNumberValidator") Validator<String> lotNumberValidator,
                                        CleanedOutSlotService cleanedOutSlotService) {
        this.lwTypeRepo = lwTypeRepo;
        this.lwRepo = lwRepo;
        this.bsRepo = bsRepo;
        this.valHelperFactory = valHelperFactory;
        this.preBarcodeValidator = preBarcodeValidator;
        this.lotNumberValidator = lotNumberValidator;
        this.cleanedOutSlotService = cleanedOutSlotService;
    }

    @Override
    public void validate(User user, Data data) {
        if (user==null) {
            data.addProblem("No user supplied.");
        }
        SlotCopyRequest request = data.request;
        if (request==null) {
            data.addProblem("No request supplied.");
            return;
        }
        ValidationHelper val = valHelperFactory.getHelper();
        if (data.opType==null) {
            data.opType = val.checkOpType(request.getOperationType());
        }
        if (data.destLabware==null) {
            data.destLabware = loadExistingDestinations(val, data.opType, request.getDestinations());
        }
        if (data.lwTypes==null) {
            data.lwTypes = loadLabwareTypes(data.problems, request.getDestinations());
        }
        checkPreBarcodes(data.problems, request.getDestinations(), data.lwTypes);
        checkPreBarcodesInUse(data.problems, request.getDestinations(), data.destLabware);
        if (data.sourceLabware == null) {
            data.sourceLabware = loadSources(data.problems, val, request.getDestinations());
        }
        checkListedSources(data.problems, request);
        validateLotNumbers(data.problems, request.getDestinations());
        validateLpNumbers(data.problems, request.getDestinations());
        validateContents(data.problems, data.lwTypes, data.sourceLabware, data.destLabware, request);
        validateOps(data.problems, request.getDestinations(), data.opType, data.lwTypes);
        data.bioStates = validateBioStates(data.problems, request.getDestinations());
        checkExistingDestinations(data.problems, request.getDestinations(), data.destLabware, data.bioStates);
        if (data.work==null) {
            data.work = val.checkWork(request.getWorkNumber());
        }
        data.problems.addAll(val.getProblems());
    }

    /**
     * Loads the specified labware types; looks for problems
     * @param problems receptacle for problems
     * @param scds the info from the request
     * @return a map of found labware types from their names
     */
    public UCMap<LabwareType> loadLabwareTypes(Collection<String> problems, List<SlotCopyDestination> scds) {
        boolean anyMissing = false;
        Set<String> lwTypeNames = new LinkedHashSet<>();
        for (SlotCopyDestination scd : scds) {
            if (nullOrEmpty(scd.getBarcode())) {
                if (nullOrEmpty(scd.getLabwareType())) {
                    anyMissing = true;
                } else {
                    lwTypeNames.add(scd.getLabwareType());
                }
            }
        }
        if (anyMissing) {
            problems.add("Labware type missing from request.");
        }
        if (lwTypeNames.isEmpty()) {
            return new UCMap<>(0);
        }
        UCMap<LabwareType> lwTypes = UCMap.from(lwTypeRepo.findAllByNameIn(lwTypeNames), LabwareType::getName);
        List<String> missing = lwTypeNames.stream()
                .filter(name -> lwTypes.get(name)==null)
                .toList();
        if (!missing.isEmpty()) {
            problems.add("Unknown labware type: "+missing);
        }
        return lwTypes;
    }

    /**
     * Loads labware specified as existing labware to put further samples into
     * @param val validation helper that checks and accumulates problems
     * @param opType the operation type
     * @param destinations the request destinations
     * @return the specified labware
     */
    public UCMap<Labware> loadExistingDestinations(ValidationHelper val,
                                                   OperationType opType,
                                                   List<SlotCopyDestination> destinations) {
        List<String> barcodes = destinations.stream()
                .map(SlotCopyDestination::getBarcode)
                .filter(s -> !nullOrEmpty(s))
                .toList();
        if (barcodes.isEmpty()) {
            return new UCMap<>(0); // none
        }
        if (opType != null && !opType.supportsActiveDest()) {
            val.getProblems().add("Reusing existing destinations is not supported for operation type "+opType.getName()+".");
        }
        return val.loadActiveDestinations(barcodes);
    }

    /**
     * Checks the prebarcodes in the request are suitable
     * @param problems receptacle for problems
     * @param scds info from the request
     * @param lwTypes loaded labware types
     */
    public void checkPreBarcodes(Collection<String> problems, List<SlotCopyDestination> scds, UCMap<LabwareType> lwTypes) {
        Set<String> missing = new LinkedHashSet<>();
        Set<String> unexpected = new LinkedHashSet<>();
        for (SlotCopyDestination scd : scds) {
            String barcode = scd.getPreBarcode();
            LabwareType lt = lwTypes.get(scd.getLabwareType());
            if (nullOrEmpty(barcode)) {
                if (lt != null && lt.isPrebarcoded()) {
                    missing.add(lt.getName());
                }
            } else if (lt != null && !lt.isPrebarcoded()) {
                unexpected.add(lt.getName());
            } else {
                preBarcodeValidator.validate(barcode.toUpperCase(), problems::add);
            }
        }
        if (!missing.isEmpty()) {
            problems.add("Expected a prebarcode for labware type: "+missing);
        }
        if (!unexpected.isEmpty()) {
            problems.add("Prebarcode not expected for labware type: "+unexpected);
        }
    }

    /**
     * Checks if any of the given prebarcodes for new labware are already in use on existing labware
     * @param problems receptacle for problems
     * @param scds info from the request
     * @param existingDestinations any existing destinations that are referenced
     */
    public void checkPreBarcodesInUse(Collection<String> problems, List<SlotCopyDestination> scds,
                                      UCMap<Labware> existingDestinations) {
        Set<String> seen = new HashSet<>(scds.size());
        for (SlotCopyDestination scd : scds) {
            String prebc = scd.getPreBarcode();
            if (nullOrEmpty(prebc)) {
                continue;
            }
            prebc = prebc.toUpperCase();
            Labware existingDest = existingDestinations.get(scd.getBarcode());
            if (existingDest!=null) {
                if (!prebc.equalsIgnoreCase(existingDest.getExternalBarcode())
                        && !prebc.equalsIgnoreCase(existingDest.getBarcode())) {
                    problems.add(String.format("External barcode %s cannot be added to existing labware %s.",
                            repr(prebc), existingDest.getBarcode()));
                }
            } else if (!seen.add(prebc)) {
                problems.add("External barcode given multiple times: "+prebc);
            } else {
                if (lwRepo.existsByBarcode(prebc)) {
                    problems.add("Labware already exists with barcode "+prebc+".");
                } else if (lwRepo.existsByExternalBarcode(prebc)) {
                    problems.add("Labware already exists with external barcode "+prebc+".");
                }
            }
        }
    }

    /**
     * Loads source labware
     * @param problems receptacle for problems
     * @param val validation helper
     * @param scds info from the request
     * @return map of source labware from its barcodes
     */
    public UCMap<Labware> loadSources(Collection<String> problems, ValidationHelper val, List<SlotCopyDestination> scds) {
        Set<String> sourceBarcodes = new HashSet<>();
        for (SlotCopyDestination scd : scds) {
            for (SlotCopyContent content : scd.getContents()) {
                String barcode = content.getSourceBarcode();
                if (nullOrEmpty(barcode)) {
                    problems.add("Missing source barcode.");
                } else {
                    sourceBarcodes.add(barcode.toUpperCase());
                }
            }
        }
        return val.checkLabware(sourceBarcodes);
    }

    /**
     * Checks the sources and states specified in the request are reasonable.
     * @param problems receptacle for problems
     * @param request the request
     */
    public void checkListedSources(Collection<String> problems, SlotCopyRequest request) {
        if (request.getSources().isEmpty()) {
            return;
        }
        Set<String> usedSourceBarcodes = request.getDestinations().stream()
                .flatMap(dest -> dest.getContents().stream())
                .map(SlotCopyContent::getSourceBarcode)
                .filter(bc -> !nullOrEmpty(bc))
                .map(String::toUpperCase)
                .collect(toSet());
        checkListedSources(problems, request.getSources(), usedSourceBarcodes);
    }

    /**
     * Checks that the sources listed with labware states are correctly filled in and match
     * the sources listed in the transfers
     * @param problems receptacle for problems
     * @param scSources the source barcodes and labware states
     * @param usedSourceBarcodes the source barcodes referenced in the transfers (upper case)
     */
    public void checkListedSources(Collection<String> problems, Collection<SlotCopySource> scSources,
                                   Set<String> usedSourceBarcodes) {
        Set<String> seen = new HashSet<>();
        Set<Labware.State> allowedStates = EnumSet.of(Labware.State.active, Labware.State.discarded, Labware.State.used);
        for (var src : scSources) {
            String bc = src.getBarcode();
            if (nullOrEmpty(bc)) {
                problems.add("Source specified without barcode.");
                continue;
            }
            bc = bc.toUpperCase();
            if (!seen.add(bc)) {
                problems.add("Repeated source barcode: "+bc);
            } else if (src.getLabwareState()==null) {
                problems.add("Source specified without labware state: " + bc);
            } else if (!allowedStates.contains(src.getLabwareState())) {
                problems.add("Unsupported new labware state: "+src.getLabwareState());
            }
        }

        seen.removeAll(usedSourceBarcodes);
        if (!seen.isEmpty()) {
            problems.add("Source barcodes specified that do not map to any destination slots: "+seen);
        }
    }

    /**
     * Checks that the lot numbers are valid where present
     * @param problems receptacle for problems
     * @param scds info from the request
     */
    public void validateLotNumbers(Collection<String> problems, Collection<SlotCopyDestination> scds) {
        for (SlotCopyDestination scd : scds) {
            if (!nullOrEmpty(scd.getLotNumber())) {
                lotNumberValidator.validate(scd.getLotNumber(), problems::add);
            }
            if (!nullOrEmpty(scd.getProbeLotNumber())) {
                lotNumberValidator.validate(scd.getProbeLotNumber(), problems::add);
            }
        }
    }

    /**
     * Checks LP numbers in the request, if given.
     * The expected format is "LP#" where # is a positive integer.
     * A sanitised version of the value will be stored back inside the request.
     * @param problems receptacle for problems
     * @param scds info from the request
     */
    public void validateLpNumbers(Collection<String> problems, Collection<SlotCopyDestination> scds) {
        Set<String> invalidLpNumbers = new LinkedHashSet<>();
        for (SlotCopyDestination scd : scds) {
            String lpNumber = scd.getLpNumber();
            if (lpNumber==null) {
                continue;
            }
            lpNumber = lpNumber.trim().toUpperCase();
            if (lpNumber.isEmpty()) {
                scd.setLpNumber(null);
                continue;
            }
            if (!LP_NUMBER_PTN.matcher(lpNumber).matches()) {
                invalidLpNumbers.add(lpNumber);
            } else {
                if (!lpNumber.startsWith("LP")) {
                    lpNumber = "LP" + lpNumber;
                }
                scd.setLpNumber(lpNumber);
            }
        }
        if (!invalidLpNumbers.isEmpty()) {
            problems.add("Unrecognised format for LP number: "+reprCollection(invalidLpNumbers));
        }
    }

    /**
     * Validates that the instructions of what to copy are valid for the source labware and new labware type
     * @param problems the receptacle for problems
     * @param lwTypes the types of the destination labware (values be null if a valid labware type was not specified)
     * @param sourceLabware the map of source barcode to labware (some labware may be missing if there were invalid/missing barcodes)
     * @param existingDestinations map of existing labware to add samples to
     * @param request the request
     */
    public void validateContents(Collection<String> problems, UCMap<LabwareType> lwTypes, UCMap<Labware> sourceLabware,
                                 UCMap<Labware> existingDestinations, SlotCopyRequest request) {
        if (nullOrEmpty(request.getDestinations())) {
            problems.add("No destinations specified.");
            return;
        }
        if (request.getDestinations().stream()
                .anyMatch(dest -> dest==null || nullOrEmpty(dest.getContents()))) {
            problems.add("No contents specified in destination.");
        }
        for (var scd : request.getDestinations()) {
            if (scd==null || nullOrEmpty(scd.getContents())) {
                continue;
            }
            Set<SlotCopyContent> contentSet = new HashSet<>(scd.getContents().size());
            Labware destLw = existingDestinations.get(scd.getBarcode());
            LabwareType lt = destLw != null ? destLw.getLabwareType() : lwTypes.get(scd.getLabwareType());
            for (var content : scd.getContents()) {
                Address destAddress = content.getDestinationAddress();
                if (!contentSet.add(content)) {
                    problems.add("Repeated copy specified: "+content);
                    continue;
                }
                if (destAddress == null) {
                    problems.add("No destination address specified.");
                } else if (destLw != null) {
                    Slot slot = destLw.optSlot(destAddress).orElse(null);
                    if (slot==null) {
                        problems.add(String.format("No such slot %s in labware %s.", destAddress, destLw.getBarcode()));
                    } else if (!slot.getSamples().isEmpty()) {
                        problems.add(String.format("Slot %s in labware %s is not empty.", destAddress, destLw.getBarcode()));
                    }
                } else if (lt != null && lt.indexOf(destAddress) < 0) {
                    problems.add("Invalid address " + destAddress + " for labware type " + lt.getName() + ".");
                }
                Address sourceAddress = content.getSourceAddress();
                Labware lw = sourceLabware.get(content.getSourceBarcode());
                if (sourceAddress == null) {
                    problems.add("No source address specified.");
                } else if (lw != null && lw.getLabwareType().indexOf(sourceAddress) < 0) {
                    problems.add("Invalid address " + sourceAddress + " for source labware " + lw.getBarcode() + ".");
                } else if (lw != null && lw.getSlot(sourceAddress).getSamples().isEmpty()) {
                    problems.add(String.format("Slot %s in labware %s is empty.", sourceAddress, lw.getBarcode()));
                }
            }
        }
        checkCleanedOutDestinations(problems, request, existingDestinations);
    }

    /**
     * Checks if request wants to put samples into any slots that have been previously cleaned out
     * @param problems receptacle for problems
     * @param request the transfer request
     * @param existingDestinations map of existing destinations from barcode
     */
    public void checkCleanedOutDestinations(Collection<String> problems, SlotCopyRequest request,
                                            UCMap<Labware> existingDestinations) {
        if (existingDestinations.isEmpty()) {
            return;
        }
        Set<Slot> cleanedOutSlots = cleanedOutSlotService.findCleanedOutSlots(existingDestinations.values());
        if (cleanedOutSlots.isEmpty()) {
            return;
        }
        UCMap<Set<Address>> badSlots = new UCMap<>();
        for (var scd : request.getDestinations()) {
            if (scd==null || nullOrEmpty(scd.getContents())) {
                continue;
            }
            Labware lw = existingDestinations.get(scd.getBarcode());
            if (lw==null) {
                continue;
            }
            for (var content : scd.getContents()) {
                Address destAddress = content.getDestinationAddress();
                if (destAddress != null) {
                    lw.optSlot(destAddress).filter(cleanedOutSlots::contains).ifPresent(slot ->
                        badSlots.computeIfAbsent(lw.getBarcode(), k -> new LinkedHashSet<>()).add(slot.getAddress())
                    );
                }
            }
        }
        for (var entry : badSlots.entrySet()) {
            problems.add("Cannot add samples to cleaned out slots in labware "+entry.getKey()+": "+entry.getValue());
        }
    }

    /**
     * Validate some specifics for the ops being requested
     * @param problems receptacle for problems
     * @param scds the requested destinations
     * @param opType the operation type
     * @param lwTypes map to get lw types by name
     */
    public void validateOps(Collection<String> problems, List<SlotCopyDestination> scds, OperationType opType,
                            UCMap<LabwareType> lwTypes) {
        if (opType!=null && opType.getName().equalsIgnoreCase(CYTASSIST_OP)) {
            for (SlotCopyDestination dest : scds) {
                validateCytOp(problems, dest.getContents(), lwTypes.get(dest.getLabwareType()));
            }
        }
    }

    /**
     * Validates some things specific to the cytassist operation
     * @param problems receptacle for problems
     * @param contents the transfer contents specified in the request
     * @param lwType the labware type for this destination
     */
    public void validateCytOp(Collection<String> problems, Collection<SlotCopyContent> contents, LabwareType lwType) {
        if (lwType != null) {
            if (!lwType.isCytAssist()) {
                problems.add(String.format("Expected a CytAssist labware type for operation %s.", CYTASSIST_OP));
            } else if (lwType.blockMiddleSlots() && contents != null && !contents.isEmpty()) {
                for (SlotCopyContent content : contents) {
                    Address ad = content.getDestinationAddress();
                    if (ad != null && ad.getColumn()==1 && ad.getRow() > 1 && ad.getRow() < 4) {
                        problems.add("Slots B1 and C1 are disallowed for use in this operation.");
                        break;
                    }
                }
            }
        }
    }

    /**
     * Loads and checks the indicated bio states (where present).
     * Bio states must exist, be valid for this operation, and must not contradict existing labware
     * @param problems receptacle for problems
     * @param scds info from request
     * @return the loaded bio states, mapped from their names
     */
    public UCMap<BioState> validateBioStates(Collection<String> problems, Collection<SlotCopyDestination> scds) {
        Set<String> bsNames = new HashSet<>();
        for (SlotCopyDestination scd : scds) {
            if (scd!=null && !nullOrEmpty(scd.getBioState())) {
                bsNames.add(scd.getBioState());
            }
        }
        if (bsNames.isEmpty()) {
            return new UCMap<>();
        }
        UCMap<BioState> bioStates = UCMap.from(bsRepo.findAllByNameIn(bsNames), BioState::getName);
        Set<String> unknown = bsNames.stream()
                .filter(name -> !bioStates.containsKey(name))
                .collect(toSet());
        if (!unknown.isEmpty()) {
            problems.add("Unknown bio state: "+unknown);
        }
        Set<String> wrongBs = bioStates.values().stream()
                .map(BioState::getName)
                .filter(name -> !VALID_BS_UPPER.contains(name.toUpperCase()))
                .collect(toSet());
        if (!wrongBs.isEmpty()) {
            problems.add("Bio state not allowed for this operation: "+wrongBs);
        }
        return bioStates;
    }

    /**
     * Checks that the information is correct if given for existing destination labware
     * @param problems receptacle for problems
     * @param scds the request destinations
     * @param labware map of labware from barcode
     * @param bs map of bio states from name
     */
    public void checkExistingDestinations(Collection<String> problems, List<SlotCopyDestination> scds,
                                          UCMap<Labware> labware, UCMap<BioState> bs) {
        for (SlotCopyDestination scd : scds) {
            if (nullOrEmpty(scd.getBarcode())) {
                continue;
            }
            Labware lw = labware.get(scd.getBarcode());
            if (lw==null) {
                continue;
            }
            if (!nullOrEmpty(scd.getLabwareType()) && !lw.getLabwareType().getName().equalsIgnoreCase(scd.getLabwareType())) {
                problems.add(String.format("Labware type %s specified for labware %s but it has type %s.",
                        scd.getLabwareType(), lw.getBarcode(), lw.getLabwareType().getName()));
            }
            checkExistingLabwareBioState(problems, lw, bs.get(scd.getBioState()));
        }
    }

    /**
     * Checks that specified bio state matches existing labware
     * @param problems receptacle for problems
     * @param lw existing labware, if any
     * @param newBs specified bio state, if any
     */
    public void checkExistingLabwareBioState(Collection<String> problems, Labware lw, BioState newBs) {
        if (lw!=null && newBs!=null) {
            Set<BioState> lwBs = lw.getSlots().stream()
                    .flatMap(slot -> slot.getSamples().stream().map(Sample::getBioState))
                    .collect(toSet());
            if (lwBs.size() == 1) {
                BioState oldBs = lwBs.iterator().next();
                if (!oldBs.equals(newBs)) {
                    problems.add(String.format("Bio state %s specified for labware %s, which already uses bio state %s.",
                            newBs.getName(), lw.getBarcode(), oldBs.getName()));
                }
            }
        }
    }
}
