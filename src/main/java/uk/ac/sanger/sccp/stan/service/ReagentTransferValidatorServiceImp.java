package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.model.LabwareType;
import uk.ac.sanger.sccp.stan.model.reagentplate.ReagentPlate;
import uk.ac.sanger.sccp.stan.request.ReagentTransferRequest;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.pluralise;

/**
 * @author dr6
 */
@Service
public class ReagentTransferValidatorServiceImp implements ReagentTransferValidatorService {
    private final Validator<String> reagentPlateBarcodeValidator;

    @Autowired
    public ReagentTransferValidatorServiceImp(
            @Qualifier("reagentPlateBarcodeValidator") Validator<String> reagentPlateBarcodeValidator
    ) {
        this.reagentPlateBarcodeValidator = reagentPlateBarcodeValidator;
    }

    @Override
    public void validateTransfers(Collection<String> problems, Collection<ReagentTransferRequest.ReagentTransfer> transfers,
                                  UCMap<ReagentPlate> reagentPlates, LabwareType lt) {
        if (transfers==null || transfers.isEmpty()) {
            problems.add("No transfers specified.");
            return;
        }
        boolean missingPlateBarcodes = false;
        boolean missingReagentAddresses = false;
        boolean missingDestAddresses = false;
        Set<ReagentTransferServiceImp.RSlot> seenReagentSlots = new HashSet<>();
        Set<ReagentTransferServiceImp.RSlot> invalidReagentSlots = new LinkedHashSet<>();
        Set<ReagentTransferServiceImp.RSlot> alreadyUsedReagentSlots = new LinkedHashSet<>();
        Set<ReagentTransferServiceImp.RSlot> repeatedReagentSlots = new LinkedHashSet<>();
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
            } else if (lt!=null) {
                if (lt.indexOf(dAddress) < 0) {
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
     * Checks for problems with the reagent slot address
     * @param invalidReagentSlots receptacle for invalid reagent slots found
     * @param alreadyUsedReagentSlots receptacle for already used reagent slots found
     * @param repeatedReagentSlots receptacle for repeated reagent slots found
     * @param seenRSlots running collection of reagent slots specified, to check for dupes
     * @param barcode the reagent plate barcode specified
     * @param rAddress the reagent slot address specified
     * @param plate the existing reagent plate (if any) matching the given barcode
     */
    private void checkReagentSlotAddress(Set<ReagentTransferServiceImp.RSlot> invalidReagentSlots, Set<ReagentTransferServiceImp.RSlot> alreadyUsedReagentSlots,
                                         Set<ReagentTransferServiceImp.RSlot> repeatedReagentSlots, Set<ReagentTransferServiceImp.RSlot> seenRSlots,
                                         String barcode, Address rAddress, ReagentPlate plate) {
        ReagentTransferServiceImp.RSlot rslot = new ReagentTransferServiceImp.RSlot(rAddress, plate!=null ? plate.getBarcode() : barcode);
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
            if (ReagentPlate.PLATE_LAYOUT_96.indexOf(rAddress) < 0) {
                invalidReagentSlots.add(new ReagentTransferServiceImp.RSlot(rAddress, barcode));
            }
        }
        if (!seenRSlots.add(rslot)) {
            repeatedReagentSlots.add(rslot);
        }
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
}
