package uk.ac.sanger.sccp.stan.service;

import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.LabwareRepo;
import uk.ac.sanger.sccp.stan.repo.MeasurementRepo;
import uk.ac.sanger.sccp.stan.request.ControlType;
import uk.ac.sanger.sccp.stan.request.VisiumPermData;
import uk.ac.sanger.sccp.stan.request.VisiumPermData.AddressPermData;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

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

    public VisiumPermDataService(LabwareRepo lwRepo, MeasurementRepo measurementRepo) {
        this.lwRepo = lwRepo;
        this.measurementRepo = measurementRepo;
    }

    /**
     * Gets labware and its visium perm data (perm times, controls and selected times)
     * @param barcode the barcode of the labware
     * @return the visium perm data for the indicated labware
     */
    public VisiumPermData load(String barcode) {
        Labware lw = lwRepo.getByBarcode(barcode);
        List<Integer> slotIds = lw.getSlots().stream().map(Slot::getId).collect(toList());
        List<Measurement> measurements = measurementRepo.findAllBySlotIdIn(slotIds);
        List<AddressPermData> pds = compilePermData(lw, measurements);
        return new VisiumPermData(lw, pds);
    }

    /**
     * Converts measurements to perm data
     * @param lw the labware the measurements are recorded on
     * @param measurements the measurements to convert
     * @return perm data of the given measurements
     */
    public List<AddressPermData> compilePermData(Labware lw, Collection<Measurement> measurements) {
        Map<Integer, Address> slotIdToAddress = lw.getSlots().stream()
                .collect(toMap(Slot::getId, Slot::getAddress));
        Measurement selected = null;
        final LinkedHashSet<AddressPermData> pds = new LinkedHashSet<>(measurements.size());
        Iterable<Measurement> sortedMeas = () -> measurements.stream()
                .sorted(Comparator.comparing(Measurement::getId))
                .iterator();
        for (Measurement meas : sortedMeas) {
            if (meas.getName().equalsIgnoreCase(SELECTED_TIME)) {
                selected = meas;
            } else if (meas.getName().equalsIgnoreCase(PERM_TIME)) {
                pds.add(new AddressPermData(slotIdToAddress.get(meas.getSlotId()), Integer.valueOf(meas.getValue())));
            } else if (meas.getName().equalsIgnoreCase(CONTROL)) {
                pds.add(new AddressPermData(slotIdToAddress.get(meas.getSlotId()), ControlType.valueOf(meas.getValue().toLowerCase())));
            }
        }
        if (selected!=null) {
            Address address = slotIdToAddress.get(selected.getSlotId());
            for (AddressPermData pd : pds) {
                if (pd.getAddress().equals(address) && pd.getSeconds()!=null && pd.getSeconds().toString().equals(selected.getValue())) {
                    pd.setSelected(true);
                }
            }
        }
        return new ArrayList<>(pds);
    }
}
