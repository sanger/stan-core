package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.SlotRepo;
import uk.ac.sanger.sccp.stan.request.CleanOutRequest;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelper;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelperFactory;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;

import static java.util.Collections.singletonList;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * @author dr6
 */
@Service
public class CleanOutServiceImp implements CleanOutService {
    private final ValidationHelperFactory valFactory;
    private final OperationService opService;
    private final WorkService workService;
    private final SlotRepo slotRepo;

    @Autowired
    public CleanOutServiceImp(ValidationHelperFactory valFactory, OperationService opService, WorkService workService,
                              SlotRepo slotRepo) {
        this.valFactory = valFactory;
        this.opService = opService;
        this.workService = workService;
        this.slotRepo = slotRepo;
    }

    @Override
    public OperationResult perform(User user, CleanOutRequest request) throws ValidationException {
        ValidationHelper val = valFactory.getHelper();
        final Collection<String> problems = val.getProblems();
        if (user==null) {
            problems.add("No user supplied.");
        }
        if (request==null) {
            problems.add("No request supplied.");
            throw new ValidationException(problems);
        }
        OperationType opType = val.checkOpType("Clean out", OperationTypeFlag.IN_PLACE);
        Work work = workService.validateUsableWork(problems, request.getWorkNumber());
        Labware lw = loadLabware(val, request.getBarcode());
        checkAddresses(problems, lw, request.getAddresses());
        if (!problems.isEmpty()) {
            throw new ValidationException(problems);
        }
        return record(user, opType, work, lw, request.getAddresses());
    }

    /**
     * Loads the indicated labware and checks for problems
     * @param val validation helper
     * @param barcode barcode of labware to load
     * @return the labware loaded, if any
     */
    public Labware loadLabware(ValidationHelper val, String barcode) {
        UCMap<Labware> labware = val.checkLabware(singletonList(barcode));
        return labware.get(barcode);
    }

    /**
     * Checks addresses for problems
     * @param problems receptacle for problems
     * @param lw labware, if known
     * @param addresses the slot addresses supplied in the request
     */
    public void checkAddresses(Collection<String> problems, Labware lw, List<Address> addresses) {
        if (nullOrEmpty(addresses)) {
            problems.add("No slot addresses supplied.");
            return;
        }
        boolean anyNull = false;
        Set<Address> uniqueAddresses = new LinkedHashSet<>();
        Set<Address> repeatedAddresses = new LinkedHashSet<>();
        for (Address address : addresses) {
            if (address==null) {
                anyNull = true;
            } else if (!uniqueAddresses.add(address)) {
                repeatedAddresses.add(address);
            }
        }
        if (anyNull) {
            problems.add("Null supplied as slot address.");
        }
        if (!repeatedAddresses.isEmpty()) {
            problems.add("Repeated slot address: "+repeatedAddresses);
        }
        if (lw!=null && !uniqueAddresses.isEmpty()) {
            checkSlots(problems, lw, uniqueAddresses);
        }
    }

    /**
     * Checks the validity of the addresses for slots in the given labware
     * @param problems receptacle for problems
     * @param lw the specified labware
     * @param addresses the specified addresses
     */
    public void checkSlots(Collection<String> problems, Labware lw, Set<Address> addresses) {
        List<Address> invalidAddresses = new ArrayList<>();
        List<Address> emptySlotAddresses = new ArrayList<>();
        for (Address address : addresses) {
            Optional<Slot> optSlot = lw.optSlot(address);
            if (optSlot.isEmpty()) {
                invalidAddresses.add(address);
            } else {
                Slot slot = optSlot.get();
                if (slot.getSamples().isEmpty()) {
                    emptySlotAddresses.add(address);
                }
            }
        }
        if (!invalidAddresses.isEmpty()) {
            problems.add("No slot found in labware "+lw.getBarcode()+" at address: "+invalidAddresses);
        }
        if (!emptySlotAddresses.isEmpty()) {
            problems.add("Slot in labware "+lw.getBarcode()+" is empty: "+emptySlotAddresses);
        }
    }

    /**
     * Records the indicates operation and cleans out the specified slots
     * @param user the user responsible for the operation
     * @param opType the operation type to record
     * @param work the work to link to the operation
     * @param lw the labware affected
     * @param addresses the addresses of the slots to clean out
     * @return the operation and labware
     */
    public OperationResult record(User user, OperationType opType, Work work, Labware lw, Collection<Address> addresses) {
        List<Slot> slots = addresses.stream()
                .map(lw::getSlot)
                .toList();
        List<Action> actions = slots.stream()
                .flatMap(slot -> slot.getSamples().stream()
                        .map(sam -> new Action(null, null, slot, slot, sam, sam)))
                .toList();
        for (Slot slot : slots) {
            slot.setSamples(List.of());
        }
        slotRepo.saveAll(slots);
        List<Operation> ops = List.of(opService.createOperation(opType, user, actions, null));
        workService.link(work, ops);
        return new OperationResult(ops, List.of(lw));
    }
}
