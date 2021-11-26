package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.*;
import uk.ac.sanger.sccp.stan.service.sanitiser.Sanitiser;
import uk.ac.sanger.sccp.stan.service.work.WorkService;

import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.pluralise;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * @author dr6
 */
@Service
public class OpWithSlotMeasurementsServiceImp implements OpWithSlotMeasurementsService {
    public static final String OP_CDNA_AMP = "cDNA amplification", OP_CDNA_ANALYSIS = "cDNA analysis";
    public static final String MEAS_CQ = "Cq value", MEAS_CONC = "Concentration";

    private final OperationTypeRepo opTypeRepo;
    private final MeasurementRepo measurementRepo;
    private final LabwareRepo lwRepo;

    private final LabwareValidatorFactory labwareValidatorFactory;
    private final Sanitiser<String> cqSanitiser;
    private final Sanitiser<String> concentrationSanitiser;
    private final WorkService workService;
    private final OperationService opService;

    public OpWithSlotMeasurementsServiceImp(OperationTypeRepo opTypeRepo, MeasurementRepo measurementRepo,
                                            LabwareRepo lwRepo,
                                            LabwareValidatorFactory labwareValidatorFactory,
                                            @Qualifier("cqSanitiser") Sanitiser<String> cqSanitiser,
                                            @Qualifier("concentrationSanitiser") Sanitiser<String> concentrationSanitiser,
                                            WorkService workService, OperationService opService) {
        this.opTypeRepo = opTypeRepo;
        this.measurementRepo = measurementRepo;
        this.lwRepo = lwRepo;
        this.labwareValidatorFactory = labwareValidatorFactory;
        this.cqSanitiser = cqSanitiser;
        this.concentrationSanitiser = concentrationSanitiser;
        this.workService = workService;
        this.opService = opService;
    }

    @Override
    public OperationResult perform(User user, OpWithSlotMeasurementsRequest request) {
        final Set<String> problems = new LinkedHashSet<>();
        if (user==null) {
            problems.add("No user specified.");
        }
        if (request==null) {
            problems.add("No request specified.");
            throw new ValidationException("The request could not be validated.", problems);
        }
        Labware lw = validateLabware(problems, request.getBarcode());
        OperationType opType = loadOpType(problems, request.getOperationType());
        Work work = workService.validateUsableWork(problems, request.getWorkNumber());
        if (lw!=null) {
            validateAddresses(problems, lw, request.getSlotMeasurements());
        }
        List<SlotMeasurementRequest> sanitisedMeasurements = sanitiseMeasurements(problems, opType, request.getSlotMeasurements());
        checkForDupeMeasurements(problems, sanitisedMeasurements);

        if (!problems.isEmpty()) {
            throw new ValidationException("The request could not be validated.", problems);
        }

        return execute(user, lw, opType, work, sanitisedMeasurements);
    }

    /**
     * Loads and checks the labware from the given barcode
     * @param problems receptacle for problems found
     * @param barcode the labware barcode to load
     * @return the labware loaded, if any
     * @see LabwareValidator
     */
    public Labware validateLabware(Collection<String> problems, String barcode) {
        if (barcode==null || barcode.isEmpty()) {
            problems.add("No barcode specified.");
            return null;
        }
        LabwareValidator validator = labwareValidatorFactory.getValidator();
        List<Labware> lwList = validator.loadLabware(lwRepo, List.of(barcode));
        validator.validateSources();
        problems.addAll(validator.getErrors());
        return (lwList.isEmpty() ? null : lwList.get(0));
    }

    /**
     * Loads the op type and checks that it is in-place
     * @param problems receptacle for problems found
     * @param opTypeName the name of the operation to record
     * @return the op type loaded
     */
    public OperationType loadOpType(Collection<String> problems, String opTypeName) {
        if (opTypeName==null || opTypeName.isEmpty()) {
            problems.add("No operation type specified.");
            return null;
        }
        var optOpType = opTypeRepo.findByName(opTypeName);
        if (optOpType.isEmpty()) {
            problems.add("Unknown operation type: "+repr(opTypeName));
            return null;
        }
        OperationType opType = optOpType.get();
        if (!opType.inPlace()) {
            problems.add("Operation cannot be recorded in place: "+opType.getName());
        } else if (!opType.getName().equalsIgnoreCase(OP_CDNA_AMP) && !opType.getName().equalsIgnoreCase(OP_CDNA_ANALYSIS)) {
            problems.add("Operation not expected for this request: "+opType.getName());
        }
        return opType;
    }

    /**
     * Checks that the addresses given in the measurement requests are present and valid.
     * @param problems receptacle for problems found
     * @param lw the labware to which the measurements refer
     * @param slotMeasurements the requested measurements
     */
    public void validateAddresses(Collection<String> problems, Labware lw, List<SlotMeasurementRequest> slotMeasurements) {
        Set<Address> invalidAddresses = new LinkedHashSet<>();
        Set<Address> emptyAddresses = new LinkedHashSet<>();
        boolean nullAddress = false;
        for (SlotMeasurementRequest smr : slotMeasurements) {
            final Address address = smr.getAddress();
            if (address ==null) {
                nullAddress = true;
            } else {
                var optSlot = lw.optSlot(address);
                if (optSlot.isEmpty()) {
                    invalidAddresses.add(address);
                } else {
                    if (optSlot.get().getSamples().isEmpty()) {
                        emptyAddresses.add(address);
                    }
                }
            }
        }
        if (nullAddress) {
            problems.add("Missing address for measurement.");
        }
        if (!invalidAddresses.isEmpty()) {
            problems.add(pluralise("Invalid address{es} for labware: ", invalidAddresses.size()) + invalidAddresses);
        }
        if (!emptyAddresses.isEmpty()) {
            problems.add(pluralise("Slot{s} {is|are} empty: ", emptyAddresses.size()) + emptyAddresses);
        }
    }

    /**
     * Validates and sanitises measurement names and values.
     * Problems include missing names, missing values, invalid names (for the op type), and whatever problems are
     * found by {@link #sanitiseMeasurementValue}.
     * @param problems receptacle for problems found
     * @param opType the op type requested
     * @param slotMeasurements the requested measurements
     * @return the validated measurements
     */
    public List<SlotMeasurementRequest> sanitiseMeasurements(Collection<String> problems, OperationType opType,
                                                             Collection<SlotMeasurementRequest> slotMeasurements) {
        if (slotMeasurements.isEmpty()) {
            return List.of();
        }
        Set<String> invalidNames = new LinkedHashSet<>();

        List<SlotMeasurementRequest> sanitised = new ArrayList<>(slotMeasurements.size());
        for (SlotMeasurementRequest smr : slotMeasurements) {
            var sanitisedSmr = sanitiseMeasurement(problems, invalidNames,
                    opType, smr);
            if (sanitisedSmr != null) {
                sanitised.add(sanitisedSmr);
            }
        }
        if (!invalidNames.isEmpty()) {
            problems.add("Unexpected measurements given for operation "+opType.getName()+": "+invalidNames);
        }
        return sanitised;
    }

    /**
     * Returns a sanitised version of a {@link SlotMeasurementRequest} if it is valid.
     * @param problems receptacle for problems
     * @param invalidNames receptacle for invalid measurement names
     * @param opType the type of op being requested
     * @param smr the incoming measurement request
     * @return a sanitised measurement request, or null if the given details are not salvagable
     */
    public SlotMeasurementRequest sanitiseMeasurement(Collection<String> problems, Set<String> invalidNames,
                                                      OperationType opType, SlotMeasurementRequest smr) {
        String name = smr.getName();
        String value = smr.getValue();
        if (name==null || name.isEmpty()) {
            problems.add("Missing name for measurement.");
            name = null;
        }
        if (value==null || value.isEmpty()) {
            problems.add("Missing value for measurement.");
            value = null;
        }

        if (name !=null && opType !=null) {
            String sanName = sanitiseMeasurementName(opType, name);
            if (sanName==null) {
                invalidNames.add(name);
            }
            name = sanName;
        }

        if (opType !=null && name != null && value != null) {
            value = sanitiseMeasurementValue(problems, name, value);
        }
        if (opType !=null && name !=null && smr.getAddress()!=null && value !=null) {
            return new SlotMeasurementRequest(smr.getAddress(), name, value);
        }
        return null;
    }

    /**
     * Sanitises the measurement name.
     * @param opType the type of op being requested
     * @param name the given name of the measurement
     * @return the sanitised name, or null if the given name is not valid for the op type
     */
    public String sanitiseMeasurementName(OperationType opType, String name) {
        if (name==null || opType==null) {
            return null;
        }
        if (opType.getName().equalsIgnoreCase(OP_CDNA_AMP)) {
            if (name.equalsIgnoreCase(MEAS_CQ)) {
                return MEAS_CQ;
            }
        } else {
            if (opType.getName().equalsIgnoreCase(OP_CDNA_ANALYSIS)) {
                if (name.equalsIgnoreCase(MEAS_CONC)) {
                    return MEAS_CONC;
                }
            }
        }
        return null;
    }

    /**
     * Sanitises the measurement value. This is done using a {@link Sanitiser}.
     * @param problems receptacle for problems found by the sanitiser
     * @param name the sanitised name of the measurement
     * @param value the given value
     * @return the sanitised value, or null if the measurement is found to be invalid
     */
    public String sanitiseMeasurementValue(Collection<String> problems, String name, String value) {
        switch (name) {
            case MEAS_CONC: return concentrationSanitiser.sanitise(problems, value);
            case MEAS_CQ: return cqSanitiser.sanitise(problems, value);
        }
        return null;
    }

    /**
     * Checks for occurrences where the same measurement name is requested in the same address.
     * The measurement names should already be sanitised so that they are easy to identify.
     * @param problems receptacle for problems found
     * @param smrs the measurement requests
     */
    public void checkForDupeMeasurements(Collection<String> problems, Collection<SlotMeasurementRequest> smrs) {
        if (smrs==null || smrs.size() <= 1) {
            return;
        }
        Set<SlotMeasurementRequest> seen = new HashSet<>();
        Set<SlotMeasurementRequest> repeated = new LinkedHashSet<>();
        for (SlotMeasurementRequest smr : smrs) {
            SlotMeasurementRequest key = new SlotMeasurementRequest(smr.getAddress(), smr.getName(), null);
            if (!seen.add(key)) {
                repeated.add(key);
            }
        }
        if (!repeated.isEmpty()) {
            StringBuilder sb = new StringBuilder("Measurements specified multiple times: ");
            for (SlotMeasurementRequest smr : repeated) {
                sb.append(smr.getName()).append(" in ").append(smr.getAddress()).append("; ");
            }
            sb.setLength(sb.length()-2);
            problems.add(sb.toString());
        }
    }

    /**
     * Executes the request. Records the op; links it to the given work (if any); records the measurements (if any)
     * @param user the user responsible for the request
     * @param lw the labware to record the operation and measurements on
     * @param opType the type of op to record
     * @param work the work to link to the op (if any)
     * @param sanitisedMeasurements the specification of what measurements to record
     * @return the op and labware
     */
    public OperationResult execute(User user, Labware lw, OperationType opType, Work work,
                                   Collection<SlotMeasurementRequest> sanitisedMeasurements) {
        Operation op = opService.createOperationInPlace(opType, user, lw, null, null);
        createMeasurements(op.getId(), lw, sanitisedMeasurements);
        List<Operation> ops = List.of(op);
        if (work!=null) {
            workService.link(work, ops);
        }
        return new OperationResult(ops, List.of(lw));
    }

    /**
     * Records the measurements in the database.
     * @param opId the id of the operation associated with the measurements
     * @param lw the labware to record measurements on
     * @param measurementRequests the specification of what measurements to record
     * @return the newly created measurements
     */
    public Iterable<Measurement> createMeasurements(Integer opId, Labware lw,
                                                    Collection<SlotMeasurementRequest> measurementRequests) {
        if (measurementRequests.isEmpty()) {
            return List.of();
        }
        List<Measurement> measurements = new ArrayList<>();
        for (SlotMeasurementRequest smr : measurementRequests) {
            Slot slot = lw.getSlot(smr.getAddress());
            Integer slotId = slot.getId();
            for (Sample sam : slot.getSamples()) {
                Measurement meas = new Measurement(null, smr.getName(), smr.getValue(), sam.getId(), opId, slotId);
                measurements.add(meas);
            }
        }
        return measurementRepo.saveAll(measurements);
    }
}
