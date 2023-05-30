package uk.ac.sanger.sccp.stan.service;

import org.apache.logging.log4j.util.TriConsumer;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.LabwareRepo;
import uk.ac.sanger.sccp.stan.repo.MeasurementRepo;
import uk.ac.sanger.sccp.stan.request.ControlType;
import uk.ac.sanger.sccp.stan.request.VisiumPermData;
import uk.ac.sanger.sccp.stan.request.VisiumPermData.AddressPermData;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.Ancestry;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;
import static uk.ac.sanger.sccp.utils.BasicUtils.reverseIter;

/**
 * Service to look up perm times for a labware barcode
 * @author dr6
 */
@Service
public class VisiumPermDataService {
    static final String PERM_TIME = "permeabilisation time",
            SELECTED_TIME = "selected time",
            CONTROL = "control";

    private final LabwareRepo lwRepo;
    private final MeasurementRepo measurementRepo;
    private final Ancestoriser ancestoriser;

    public VisiumPermDataService(LabwareRepo lwRepo, MeasurementRepo measurementRepo, Ancestoriser ancestoriser) {
        this.lwRepo = lwRepo;
        this.measurementRepo = measurementRepo;
        this.ancestoriser = ancestoriser;
    }

    /**
     * Gets labware and its visium perm data (perm times, controls and selected times)
     * @param barcode the barcode of the labware
     * @return the visium perm data for the indicated labware
     */
    public VisiumPermData load(String barcode) {
        Labware lw = lwRepo.getByBarcode(barcode);
        var ancestry = ancestoriser.findAncestry(Ancestoriser.SlotSample.stream(lw).collect(toList()));

        var ssToAddress = makeSlotSampleIdAddressMap(lw, ancestry);
        Set<Integer> slotIds = ancestry.keySet().stream()
                .map(ss -> ss.getSlot().getId())
                .collect(toSet());
        List<Measurement> measurements = measurementRepo.findAllBySlotIdIn(slotIds);
        List<AddressPermData> pds = compilePermData(measurements, ssToAddress);
        return new VisiumPermData(lw, pds);
    }

    /**
     * Makes a map of ancestral slot and sample id to ultimate address in the given labware
     * @param lw the labware
     * @param ancestry the ancestry of this labware
     * @return a map of ancestral slot and sample id to address in the given labware
     */
    public Map<SlotIdSampleId, Set<Address>> makeSlotSampleIdAddressMap(Labware lw, Ancestry ancestry) {
        final Map<SlotIdSampleId, Set<Address>> ssToAddress = new HashMap<>();
        final TriConsumer<Slot, Sample, Address> addToMap = (slot, sample, address) -> {
            ssToAddress.computeIfAbsent(new SlotIdSampleId(slot, sample), k -> new HashSet<>()).add(address);
        };

        for (Slot slot : lw.getSlots()) {
            for (Sample sam : slot.getSamples()) {
                addToMap.accept(slot, sam, slot.getAddress());
            }
        }

        Ancestoriser.SlotSample.stream(lw).forEach(targetSs -> {
            Address targetAddress = targetSs.getSlot().getAddress();
            for (Ancestoriser.SlotSample ancesterSs : ancestry.ancestors(targetSs)) {
                addToMap.accept(ancesterSs.getSlot(), ancesterSs.getSample(), targetAddress);
            }
        });
        return ssToAddress;
    }

    /**
     * Converts measurements to perm data
     * @param measurements the measurements to convert
     * @param ssToAddress a map of slot and sample ids to address to indicate in the data
     * @return perm data of the given measurements
     */
    public List<AddressPermData> compilePermData(Collection<Measurement> measurements,
                                                 Map<SlotIdSampleId, Set<Address>> ssToAddress) {
        final LinkedHashSet<AddressPermData> pds = new LinkedHashSet<>(measurements.size());
        List<Measurement> sortedMeasurements = measurements.stream()
                .sorted(Comparator.comparing(Measurement::getId))
                .collect(toList());

        Measurement selectedMeasurement = null;
        for (Measurement meas : reverseIter(sortedMeasurements)) {
            if (meas.getName().equalsIgnoreCase(SELECTED_TIME)) {
                selectedMeasurement = meas;
                break;
            }
        }

        for (Measurement meas : sortedMeasurements) {
            if (meas.getName().equalsIgnoreCase(PERM_TIME)) {
                SlotIdSampleId key = new SlotIdSampleId(meas.getSlotId(), meas.getSampleId());
                Set<Address> targetAddresses = ssToAddress.get(key);
                if (!nullOrEmpty(targetAddresses)) {
                    boolean selected = (selectedMeasurement!=null
                            && selectedMeasurement.getSlotId().equals(meas.getSlotId())
                            && selectedMeasurement.getSampleId().equals(meas.getSampleId())
                            && selectedMeasurement.getValue().equals(meas.getValue()));
                    Integer permValue = Integer.valueOf(meas.getValue());
                    for (Address address : targetAddresses) {
                        pds.add(new AddressPermData(address, permValue, null, selected));
                    }
                }
            } else if (meas.getName().equalsIgnoreCase(CONTROL)) {
                SlotIdSampleId key = new SlotIdSampleId(meas.getSlotId(), meas.getSampleId());
                Set<Address> targetAddresses = ssToAddress.get(key);
                if (!nullOrEmpty(targetAddresses)) {
                    ControlType controlType = ControlType.valueOf(meas.getValue().toLowerCase());
                    for (Address address : targetAddresses) {
                        pds.add(new AddressPermData(address, controlType));
                    }
                }
            }
        }
        return new ArrayList<>(pds);
    }
}
