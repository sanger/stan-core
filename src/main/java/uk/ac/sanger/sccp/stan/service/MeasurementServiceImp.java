package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.AddressString;

import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * @author dr6
 */
@Service
public class MeasurementServiceImp implements MeasurementService {
    private final LabwareRepo lwRepo;
    private final ActionRepo actionRepo;
    private final MeasurementRepo measurementRepo;

    @Autowired
    public MeasurementServiceImp(LabwareRepo lwRepo, ActionRepo actionRepo, MeasurementRepo measurementRepo) {
        this.lwRepo = lwRepo;
        this.actionRepo = actionRepo;
        this.measurementRepo = measurementRepo;
    }

    @Override
    public Map<Address, List<Measurement>> getMeasurementsFromLabwareOrParent(String barcode, String name) {
        Labware lw = lwRepo.getByBarcode(barcode);
        Map<Integer, Address> slotIdAddress = lw.getSlots().stream()
                .collect(toMap(Slot::getId, Slot::getAddress));
        Map<SlotIdSampleId, Set<Slot>> parentToSlot = getParentSlotIdMap(lw.getSlots());
        Set<Integer> allSlotIds = Stream.concat(slotIdAddress.keySet().stream(),
                        parentToSlot.keySet().stream().map(SlotIdSampleId::getSlotId))
                .collect(toSet());
        List<Measurement> measurements = measurementRepo.findAllBySlotIdInAndName(allSlotIds, name);
        if (measurements.isEmpty()) {
            return Map.of();
        }
        Map<Address, List<Measurement>> map = new HashMap<>();
        for (Measurement measurement : measurements) {
            Address ad = slotIdAddress.get(measurement.getSlotId());
            if (ad!=null) {
                map.computeIfAbsent(ad, k -> new ArrayList<>()).add(measurement);
                continue;
            }
            Set<Slot> targetSlots = parentToSlot.get(new SlotIdSampleId(measurement.getSlotId(), measurement.getSampleId()));
            if (!nullOrEmpty(targetSlots)) {
                for (Slot targetSlot : targetSlots) {
                    map.computeIfAbsent(targetSlot.getAddress(), k -> new ArrayList<>()).add(measurement);
                }
            }
        }
        return map;
    }

    @Override
    public List<AddressString> toAddressStrings(Map<Address, List<Measurement>> map) {
        if (nullOrEmpty(map)) {
            return List.of();
        }
        return map.entrySet().stream()
                .filter(e -> !nullOrEmpty(e.getValue()))
                .map(e -> new AddressString(e.getKey(), singleValue(e.getValue())))
                .toList();
    }

    /**
     * Selects a measurement value from a list of measurements.
     * Takes the value from the measurement with the highest id, since it's likely to be the latest.
     * @param measurements the measurements
     * @return a value from the measurements
     * @exception NoSuchElementException if the measurements list is empty
     */
    public String singleValue(Collection<Measurement> measurements) {
        return measurements.stream().max(Comparator.comparing(Measurement::getId))
                .map(Measurement::getValue)
                .orElseThrow();
    }

    /**
     * Gets a map from source slot/sample ids to destination slots
     * @param slots the destination slots to find actions for
     * @return a map of source slot/sample id to destination slots
     */
    public Map<SlotIdSampleId, Set<Slot>> getParentSlotIdMap(Collection<Slot> slots) {
        List<Action> actions = actionRepo.findAllByDestinationIn(slots);
        Map<SlotIdSampleId, Set<Slot>> map = new HashMap<>();
        for (Action action : actions) {
            SlotIdSampleId key = new SlotIdSampleId(action.getSource().getId(), action.getSourceSample().getId());
            map.computeIfAbsent(key, k -> new HashSet<>()).add(action.getDestination());
        }
        return map;
    }
}
