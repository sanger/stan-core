package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.reagentplate.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.ReagentTransferRequest;
import uk.ac.sanger.sccp.stan.request.ReagentTransferRequest.ReagentTransfer;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.pluralise;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * Implementation of reagent transfer service.
 * @author dr6
 */
@Service
public class ReagentTransferServiceImp implements ReagentTransferService {
    private final OperationTypeRepo opTypeRepo;
    private final ReagentActionRepo reagentActionRepo;
    private final LabwareRepo lwRepo;
    private final SampleRepo sampleRepo;
    private final SlotRepo slotRepo;

    private final Validator<String> reagentPlateBarcodeValidator;
    private final LabwareValidatorFactory lwValFactory;

    private final OperationService opService;
    private final ReagentPlateService reagentPlateService;
    private final WorkService workService;

    @Autowired
    public ReagentTransferServiceImp(OperationTypeRepo opTypeRepo, ReagentActionRepo reagentActionRepo,
                                     LabwareRepo lwRepo, SampleRepo sampleRepo, SlotRepo slotRepo,
                                     @Qualifier("reagentPlateBarcodeValidator") Validator<String> reagentPlateBarcodeValidator,
                                     LabwareValidatorFactory lwValFactory,
                                     OperationService opService, ReagentPlateService reagentPlateService,
                                     WorkService workService) {
        this.opTypeRepo = opTypeRepo;
        this.reagentActionRepo = reagentActionRepo;
        this.lwRepo = lwRepo;
        this.sampleRepo = sampleRepo;
        this.slotRepo = slotRepo;
        this.reagentPlateBarcodeValidator = reagentPlateBarcodeValidator;
        this.lwValFactory = lwValFactory;
        this.opService = opService;
        this.reagentPlateService = reagentPlateService;
        this.workService = workService;
    }

    @Override
    public OperationResult perform(User user, ReagentTransferRequest request) throws ValidationException {
        requireNonNull(user, "User is null");
        requireNonNull(request, "Request is null");
        final Set<String> problems = new LinkedHashSet<>();
        OperationType opType = loadOpType(problems, request.getOperationType());
        Labware lw = loadLabware(problems, request.getDestinationBarcode());
        Work work = workService.validateUsableWork(problems, request.getWorkNumber());
        UCMap<ReagentPlate> reagentPlates = loadReagentPlates(request.getTransfers());
        validateTransfers(problems, request.getTransfers(), reagentPlates, lw);

        if (!problems.isEmpty()) {
            throw new ValidationException("The request could not be validated.", problems);
        }

        return record(user, opType, work, request.getTransfers(), reagentPlates, lw);
    }

    /**
     * Loads and checks the operation type
     * @param problems receptacle for problems
     * @param opName the name of the op type
     * @return the operation type loaded, if any
     */
    public OperationType loadOpType(Collection<String> problems, String opName) {
        if (opName==null || opName.isEmpty()) {
            problems.add("No operation type specified.");
            return null;
        }
        var optOpType = opTypeRepo.findByName(opName);
        if (optOpType.isEmpty()) {
            problems.add("Unknown operation type: "+repr(opName));
            return null;
        }
        OperationType opType = optOpType.get();
        if (!opType.transfersReagent() || !opType.inPlace() || opType.has(OperationTypeFlag.RESULT)
                || opType.has(OperationTypeFlag.ANALYSIS) || opType.sourceMustBeBlock()) {
            problems.add("Operation type "+opType.getName()+" cannot be used in this request.");
        }
        return opType;
    }

    /**
     * Loads and checks the labware
     * @param problems receptacle for problems
     * @param barcode the barcode
     * @return the labware loaded, if any
     */
    public Labware loadLabware(Collection<String> problems, String barcode) {
        var val = lwValFactory.getValidator();
        List<Labware> lws = val.loadLabware(lwRepo, List.of(barcode));
        val.validateSources();
        problems.addAll(val.getErrors());
        return (lws.isEmpty() ? null : lws.get(0));
    }

    /**
     * Loads any reagent plates already in the database matching any of the specified barcodes.
     * Unrecognised or missing barcodes are omitted without error.
     * @param transfers reagent transfers, specifying reagent plate barcodes
     * @return a map of barcode to reagent plates
     */
    public UCMap<ReagentPlate> loadReagentPlates(Collection<ReagentTransfer> transfers) {
        Set<String> barcodes = transfers.stream()
                .map(ReagentTransfer::getReagentPlateBarcode)
                .filter(Objects::nonNull)
                .map(String::toUpperCase)
                .collect(toSet());
        return reagentPlateService.loadPlates(barcodes);
    }

    /**
     * Checks for problems with the specified transfers. Problems include:<ul>
     *     <li>invalid or missing reagent plate barcodes</li>
     *     <li>invalid or missing reagent slot addresses</li>
     *     <li>invalid or missing destination slot addresses</li>
     *     <li>reagent slot addresses already used in a previous operation</li>
     *     <li>reagent slot addresses given multiple times in this request</li>
     * </ul>
     * @param problems receptacle for problems
     * @param transfers the transfers to validate
     * @param reagentPlates the existing reagent plates
     * @param lw the destination labware
     */
    public void validateTransfers(Collection<String> problems, Collection<ReagentTransfer> transfers,
                                  UCMap<ReagentPlate> reagentPlates, Labware lw) {
        if (transfers==null || transfers.isEmpty()) {
            problems.add("No transfers specified.");
            return;
        }
        boolean missingPlateBarcodes = false;
        boolean missingReagentAddresses = false;
        boolean missingDestAddresses = false;
        Set<RSlot> seenReagentSlots = new HashSet<>();
        Set<RSlot> invalidReagentSlots = new LinkedHashSet<>();
        Set<RSlot> alreadyUsedReagentSlots = new LinkedHashSet<>();
        Set<RSlot> repeatedReagentSlots = new LinkedHashSet<>();
        Set<Address> invalidDestSlots = new LinkedHashSet<>();
        Set<String> newBarcodesSeen = new HashSet<>();

        for (var transfer : transfers) {
            String barcode = transfer.getReagentPlateBarcode();
            Address rAddress = transfer.getReagentSlotAddress();
            Address dAddress = transfer.getDestinationAddress();
            ReagentPlate plate;
            if (barcode==null || barcode.isEmpty()) {
                missingPlateBarcodes = true;
                plate = null;
                barcode = null;
            } else {
                plate = reagentPlates.get(barcode);
                if (plate==null && newBarcodesSeen.add(barcode.toUpperCase())) {
                    reagentPlateBarcodeValidator.validate(barcode, problems::add);
                }
            }
            if (rAddress==null) {
                missingReagentAddresses = true;
            } else if (barcode!=null) {
                checkReagentSlotAddress(invalidReagentSlots, alreadyUsedReagentSlots, repeatedReagentSlots,
                        seenReagentSlots, barcode, rAddress, plate);
            }
            if (dAddress==null) {
                missingDestAddresses = true;
            } else if (lw!=null) {
                if (lw.optSlot(dAddress).isEmpty()) {
                    invalidDestSlots.add(dAddress);
                }
            }
        }

        if (missingPlateBarcodes) {
            problems.add("Missing reagent plate barcode for transfer.");
        }
        if (missingReagentAddresses) {
            problems.add("Missing reagent slot address for transfer.");
        }
        if (missingDestAddresses) {
            problems.add("Missing destination slot address for transfer.");
        }
        describeProblem(problems, "Invalid reagent slot{s} specified: ", invalidReagentSlots);
        describeProblem(problems, "Invalid destination slot{s} specified: ", invalidDestSlots);
        describeProblem(problems, "Reagent slot{s} already used: ", alreadyUsedReagentSlots);
        describeProblem(problems, "Repeated reagent slot{s} specified: ", repeatedReagentSlots);
    }

    /**
     * Adds a problem listing the given items, if there are any
     * @param problems the receptacle for problems
     * @param description the pluralisable description of the problem
     * @param items the items associated with the problem
     */
    private static void describeProblem(Collection<String> problems, String description, Collection<?> items) {
        if (!items.isEmpty()) {
            problems.add(pluralise(description, items.size()) + items);
        }
    }

    /**
     * Checks for problems with the reagent slot address
     * @param invalidReagentSlots receptacle for invalid reagent slots found
     * @param alreadyUsedReagentSlots receptacle for already used reagent slots found
     * @param repeatedReagentSlots receptacle for repeated reagent slots found
     * @param seenRSlots running collection of reagent slots specified, to check for dupes
     * @param barcode the reagent plate barcode specified
     * @param rAddress the reagent slot address specified
     * @param plate the existing reagent plate (if any) matching the given barcode
     */
    private void checkReagentSlotAddress(Set<RSlot> invalidReagentSlots, Set<RSlot> alreadyUsedReagentSlots,
                                         Set<RSlot> repeatedReagentSlots, Set<RSlot> seenRSlots,
                                         String barcode, Address rAddress, ReagentPlate plate) {
        RSlot rslot = new RSlot(rAddress, plate!=null ? plate.getBarcode() : barcode);
        if (plate != null) {
            var optSlot = plate.optSlot(rAddress);
            if (optSlot.isEmpty()) {
                invalidReagentSlots.add(rslot);
                return;
            }
            var reagentSlot = optSlot.get();
            if (reagentSlot.isUsed()) {
                alreadyUsedReagentSlots.add(rslot);
                return;
            }
        } else {
            // Since the reagent plate doesn't exist yet, we assume its size
            // (a safe assumption, since it is the only size we support).
            if (ReagentPlate.PLATE_TYPE_96.indexOf(rAddress) < 0) {
                invalidReagentSlots.add(new RSlot(rAddress, barcode));
            }
        }
        if (!seenRSlots.add(rslot)) {
            repeatedReagentSlots.add(rslot);
        }
    }

    /**
     * Records the specified transfers.
     * @param user the user responsible
     * @param opType the type of operation to record
     * @param work the work number (if any) to link the operation with
     * @param transfers the transfers to make
     * @param reagentPlates the reagent plates that already exist
     * @param lw the destination labware
     * @return the operations and affected labware
     */
    public OperationResult record(User user, OperationType opType, Work work, Collection<ReagentTransfer> transfers,
                                  UCMap<ReagentPlate> reagentPlates, Labware lw) {
        createReagentPlates(transfers, reagentPlates);
        List<Action> actions = updateLabware(opType, lw);
        Operation op = createOperation(user, opType, work, lw, actions);
        recordTransfers(transfers, reagentPlates, lw, op.getId());
        return new OperationResult(List.of(op), List.of(lw));
    }

    /**
     * Creates reagent plates for the barcodes specified in the transfers if they are not already in the given map.
     * @param transfers the transfers requested
     * @param reagentPlates the cache and receptacle for reagent plates, mapped from their barcodes
     */
    public void createReagentPlates(Collection<ReagentTransfer> transfers,
                                    UCMap<ReagentPlate> reagentPlates) {
        for (ReagentTransfer transfer : transfers) {
            String barcode = transfer.getReagentPlateBarcode();
            if (reagentPlates.get(barcode)==null) {
                reagentPlates.put(barcode, reagentPlateService.createReagentPlate(barcode));
            }
        }
    }

    /**
     * Creates an operation in place
     * @param user the user responsible for the operation
     * @param opType the type of operation
     * @param work the work (if any) to link
     * @param lw the labware to record the operation on
     * @param actions the specific actions to record (optional)
     * @return the newly recorded operation
     */
    public Operation createOperation(User user, OperationType opType, Work work, Labware lw, List<Action> actions) {
        Operation op;
        if (actions==null) {
            op = opService.createOperationInPlace(opType, user, lw, null, null);
        } else {
            op = opService.createOperation(opType, user, actions, null);
        }
        if (work!=null) {
            workService.link(work, List.of(op));
        }
        return op;
    }

    /**
     * Updates the labware affected by the given op type being recorded on it.
     * If any samples are updated, returns <i>all</i> the appropriate actions for the operation, linking
     * all old samples to their new counterparts.
     * @param opType the type of op being recorded
     * @param lw the labware affected
     * @return a list of actions describing the source and destination samples in their appropriate slots
     */
    public List<Action> updateLabware(OperationType opType, Labware lw) {
        BioState bs = opType.getNewBioState();
        if (bs==null) {
            return null;
        }
        final List<Action> actions = new ArrayList<>();
        Map<Integer, Sample> newSamples = new HashMap<>();
        for (Slot slot : lw.getSlots()) {
            if (slot.getSamples().stream().allMatch(sam -> bs.equals(sam.getBioState()))) {
                for (Sample sample : slot.getSamples()) {
                    actions.add(new Action(null, null, slot, slot, sample, sample));
                }
            } else {
                slot.setSamples(slot.getSamples().stream()
                        .map(oldSample -> {
                            Sample newSample = replaceSample(bs, oldSample, newSamples);
                            actions.add(new Action(null, null, slot, slot, newSample, oldSample));
                            return newSample;
                        })
                        .collect(toList()));
                slotRepo.save(slot);
            }
        }
        return actions;
    }

    /**
     * Gets a sample of the given biostate to replace the given sample.
     * The sampleMap is a cache of old sample id to new sample.
     * If the old sample already has the specified bio state, it is returned.
     * Otherwise, if the old sample id already has a new sample in the cache, that is returned.
     * Otherwise, a new sample is created, added to the cache and returned.
     * @param bs the required bio state
     * @param oldSample the sample being replaced
     * @param sampleMap a map of old sample id to new (replacement) sample
     * @return the appropriate sample, in the correct bio state
     */
    public Sample replaceSample(BioState bs, Sample oldSample, Map<Integer, Sample> sampleMap) {
        if (bs.equals(oldSample.getBioState())) {
            return oldSample;
        }
        Sample newSample = sampleMap.get(oldSample.getId());
        if (newSample==null) {
            newSample = sampleRepo.save(new Sample(null, oldSample.getSection(), oldSample.getTissue(), bs));
            sampleMap.put(oldSample.getId(), newSample);
        }
        return newSample;
    }

    /**
     * Records reagent actions describing the given transfers
     * @param transfers the transfers to record
     * @param reagentPlates the map to look up reagent plates from their barcodes
     * @param lw the destination labware
     * @param opId the operation id to link with the reagent actions
     */
    public void recordTransfers(Collection<ReagentTransfer> transfers, UCMap<ReagentPlate> reagentPlates,
                                Labware lw, Integer opId) {
        List<ReagentAction> ras = transfers.stream()
                .map(ra -> {
                    ReagentPlate rp = reagentPlates.get(ra.getReagentPlateBarcode());
                    ReagentSlot rs = rp.getSlot(ra.getReagentSlotAddress());
                    Slot ds = lw.getSlot(ra.getDestinationAddress());
                    return new ReagentAction(null, opId, rs, ds);
                })
                .collect(toList());
        reagentActionRepo.saveAll(ras);
    }

    /**
     * A helper class used for deduping and tracking errors in specified slots.
     */
    static class RSlot {
        Address address;
        String barcode;

        RSlot(Address address, String barcode) {
            this.address = address;
            this.barcode = barcode.toUpperCase();
        }

        @Override
        public String toString() {
            return "slot "+address+" in reagent plate "+barcode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RSlot that = (RSlot) o;
            return (Objects.equals(this.barcode, that.barcode)
                    && Objects.equals(this.address, that.address));
        }

        @Override
        public int hashCode() {
            return Objects.hash(barcode, address);
        }
    }

}