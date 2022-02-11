package uk.ac.sanger.sccp.stan.service;

import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.VisiumAnalysisRequest;
import uk.ac.sanger.sccp.stan.service.work.WorkService;

import java.util.*;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

/**
 * @author dr6
 */
@Service
public class VisiumAnalysisServiceImp implements VisiumAnalysisService {
    private final LabwareValidatorFactory labwareValidatorFactory;
    private final WorkService workService;
    private final OperationService opService;
    private final LabwareRepo lwRepo;
    private final MeasurementRepo measurementRepo;
    private final OperationTypeRepo opTypeRepo;

    public VisiumAnalysisServiceImp(LabwareValidatorFactory labwareValidatorFactory,
                                    WorkService workService, OperationService opService,
                                    LabwareRepo lwRepo, MeasurementRepo measurementRepo, OperationTypeRepo opTypeRepo) {
        this.labwareValidatorFactory = labwareValidatorFactory;
        this.workService = workService;
        this.opService = opService;
        this.lwRepo = lwRepo;
        this.measurementRepo = measurementRepo;
        this.opTypeRepo = opTypeRepo;
    }

    @Override
    public OperationResult record(User user, VisiumAnalysisRequest request) {
        requireNonNull(user, "No user provided.");
        requireNonNull(request, "No request provided.");
        final Set<String> problems = new LinkedHashSet<>();
        Labware lw = loadLabware(problems, request.getBarcode());
        Work work = workService.validateUsableWork(problems, request.getWorkNumber());
        validateMeasurement(problems, lw, request.getSelectedAddress(), request.getSelectedTime());

        if (!problems.isEmpty()) {
            throw new ValidationException("The request could not be validated.", problems);
        }

        return recordAnalysis(user, lw, request.getSelectedAddress(), request.getSelectedTime(), work);
    }

    /**
     * Loads the labware indicated in the request. Records a problem if the labware cannot be loaded
     * or is not in a usable state.
     * @param problems receptacle for problems
     * @param barcode the barcode of the labware
     * @return the loaded labware, if any
     */
    public Labware loadLabware(Collection<String> problems, String barcode) {
        if (barcode==null || barcode.isEmpty()) {
            problems.add("No barcode supplied.");
            return null;
        }
        LabwareValidator val = labwareValidatorFactory.getValidator();
        val.loadLabware(lwRepo, List.of(barcode));
        val.validateSources();
        problems.addAll(val.getErrors());
        var lws = val.getLabware();
        if (lws.isEmpty()) {
            return null;
        }
        return lws.iterator().next();
    }

    /**
     * Validates that the specified measurement can be recorded on the given labware.
     * @param problems receptacle for problems
     * @param lw the specified labware
     * @param slotAddress the specified slot address
     * @param time the specified time
     */
    public void validateMeasurement(Collection<String> problems, Labware lw, Address slotAddress, Integer time) {
        if (slotAddress==null) {
            problems.add("No slot address specified.");
            return;
        }
        if (lw==null) {
            return;
        }
        var optSlot = lw.optSlot(slotAddress);
        if (optSlot.isEmpty()) {
            problems.add(String.format("Slot %s does not exist in labware %s.", slotAddress,
                    lw.getBarcode()));
            return;
        }
        Slot slot = optSlot.get();
        if (slot.getSamples().isEmpty()) {
            problems.add(String.format("There are no samples in slot %s of labware %s.", slot.getAddress(),
                    lw.getBarcode()));
            return;
        }
        if (time==null) {
            problems.add("No selected time specified.");
            return;
        }
        List<Measurement> measurements = measurementRepo.findAllBySlotIdIn(List.of(slot.getId()));
        String value = time.toString();
        if (measurements.stream().noneMatch(meas -> meas.getName().equalsIgnoreCase("permeabilisation time") &&
                meas.getValue().equalsIgnoreCase(value))) {
            problems.add(String.format("A permeabilisation measurement of %s seconds was not found in slot %s of labware %s.",
                    value, slot.getAddress(), lw.getBarcode()));
        }
    }

    /**
     * Records the operation and measurements and links to the indicated work
     * @param user the user responsible for the request
     * @param lw the indicated labware
     * @param selectedAddress the indicated slot address
     * @param selectedTime the specified time
     * @param work the indicated work, if any
     * @return the operations and labware
     */
    public OperationResult recordAnalysis(User user, Labware lw, Address selectedAddress, Integer selectedTime,
                                          Work work) {
        OperationType opType = opTypeRepo.getByName("Visium analysis");
        Operation op = opService.createOperationInPlace(opType, user, lw, null, null);
        createMeasurement(lw.getSlot(selectedAddress), selectedTime.toString(), op.getId());
        List<Operation> ops = List.of(op);
        if (work!=null) {
            workService.link(work, ops);
        }
        return new OperationResult(ops, List.of(lw));
    }

    /**
     * Records measurements as indicated by the request. One measurement is recorded for each sample in the
     * indicated slot.
     * @param slot the slot to record the measurements in
     * @param measurementValue the value to record for the measurements
     * @param opId the operation id associated with the measurements
     * @return newly created measurements
     */
    public Iterable<Measurement> createMeasurement(Slot slot, String measurementValue, Integer opId) {
        final Integer slotId = slot.getId();
        List<Measurement> newMeasurements = slot.getSamples().stream()
                .map(sam -> new Measurement(null, "selected time", measurementValue,
                        sam.getId(), opId, slotId))
                .collect(toList());
        return measurementRepo.saveAll(newMeasurements);
    }
}
