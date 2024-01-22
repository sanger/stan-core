package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;

import java.util.List;
import java.util.Optional;

/**
 * @author dr6
 */
@Service
public class MeasurementServiceImp implements MeasurementService {
    private final LabwareRepo lwRepo;
    private final SlotRepo slotRepo;
    private final ActionRepo actionRepo;
    private final MeasurementRepo measurementRepo;

    @Autowired
    public MeasurementServiceImp(LabwareRepo lwRepo, SlotRepo slotRepo, ActionRepo actionRepo, MeasurementRepo measurementRepo) {
        this.lwRepo = lwRepo;
        this.slotRepo = slotRepo;
        this.actionRepo = actionRepo;
        this.measurementRepo = measurementRepo;
    }

    @Override
    public Optional<Measurement> getMeasurementFromLabwareOrParent(String barcode, String name) {
        Labware lw = lwRepo.getByBarcode(barcode);
        List<Integer> slotIds = lw.getSlots().stream()
                .map(Slot::getId)
                .toList();
        List<Measurement> measurements = measurementRepo.findAllBySlotIdInAndName(slotIds, name);
        if (!measurements.isEmpty()) {
            return Optional.of(measurements.getFirst());
        }
        List<Integer> sourceLabwareIds = actionRepo.findSourceLabwareIdsForDestinationLabwareIds(List.of(lw.getId()));
        List<Integer> sourceLabwareSlotIds = slotRepo.findSlotIdsByLabwareIdIn(sourceLabwareIds);
        List<Measurement> sourceMeasurements = measurementRepo.findAllBySlotIdInAndName(sourceLabwareSlotIds, name);
        return sourceMeasurements.stream().findFirst();
    }
}
