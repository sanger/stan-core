package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;

import java.util.*;

import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * @author dr6
 */
@Service
public class CleanedOutSlotServiceImp implements CleanedOutSlotService {
    public static final String CLEAN_OUT_OP = "Clean out";

    private final LabwareRepo lwRepo;
    private final OperationTypeRepo opTypeRepo;
    private final OperationRepo opRepo;

    @Autowired
    public CleanedOutSlotServiceImp(LabwareRepo lwRepo, OperationTypeRepo opTypeRepo, OperationRepo opRepo) {
        this.lwRepo = lwRepo;
        this.opTypeRepo = opTypeRepo;
        this.opRepo = opRepo;
    }

    @Override
    public Set<Slot> findCleanedOutSlots(Collection<Labware> labware) {
        if (nullOrEmpty(labware)) {
            return Set.of();
        }
        Optional<OperationType> optOpType = opTypeRepo.findByName(CLEAN_OUT_OP);
        if (optOpType.isEmpty()) {
            return Set.of();
        }
        Set<Integer> labwareIds = labware.stream()
                .map(Labware::getId)
                .collect(toSet());
        List<Operation> ops = opRepo.findAllByOperationTypeAndDestinationLabwareIdIn(optOpType.get(), labwareIds);
        return ops.stream()
                .flatMap(op -> op.getActions().stream())
                .map(Action::getDestination)
                .filter(slot -> labwareIds.contains(slot.getLabwareId()))
                .collect(toSet());
    }

    @Override
    public List<Address> findCleanedOutAddresses(String barcode) {
        Labware lw = lwRepo.findByBarcode(barcode).orElse(null);
        if (lw==null) {
            return List.of();
        }
        return findCleanedOutSlots(List.of(lw)).stream()
                .map(Slot::getAddress)
                .toList();
    }
}
