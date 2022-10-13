package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.*;
import uk.ac.sanger.sccp.stan.request.RecordPermRequest.PermData;
import uk.ac.sanger.sccp.stan.service.work.WorkService;

import java.util.*;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * @author dr6
 */
@Service
public class PermServiceImp implements PermService {
    private final LabwareValidatorFactory labwareValidatorFactory;
    private final OperationService opService;
    private final WorkService workService;
    private final LabwareRepo labwareRepo;
    private final OperationTypeRepo opTypeRepo;
    private final OperationRepo opRepo;
    private final MeasurementRepo measurementRepo;
    private final SlotRepo slotRepo;

    @Autowired
    public PermServiceImp(LabwareValidatorFactory labwareValidatorFactory, OperationService opService,
                          WorkService workService,
                          LabwareRepo labwareRepo, OperationTypeRepo opTypeRepo, OperationRepo opRepo,
                          MeasurementRepo measurementRepo, SlotRepo slotRepo) {
        this.labwareValidatorFactory = labwareValidatorFactory;
        this.opService = opService;
        this.workService = workService;
        this.labwareRepo = labwareRepo;
        this.opTypeRepo = opTypeRepo;
        this.opRepo = opRepo;
        this.measurementRepo = measurementRepo;
        this.slotRepo = slotRepo;
    }

    @Override
    public OperationResult recordPerm(User user, RecordPermRequest request) {
        requireNonNull(user, "User is null");
        requireNonNull(request, "Request is null");
        final Set<String> problems = new LinkedHashSet<>();
        Labware lw = lookUpLabware(problems, request.getBarcode());
        Labware controlLabware = lookUpControlLabware(problems, request.getPermData());
        validateLabware(problems, lw);
        validateControlLabware(problems, controlLabware);
        validatePermData(problems, lw, request.getPermData());
        Work work = workService.validateUsableWork(problems, request.getWorkNumber());
        if (!problems.isEmpty()) {
            throw new ValidationException("The request could not be validated.", problems);
        }
        return record(user, lw, request.getPermData(), controlLabware, work);
    }

    /**
     * Loads the indicated item of labware.
     * Adds a problem if the labware is not found.
     * Does not check that the labware is suitable: that is another method.
     * @param problems receptacle for problems
     * @param barcode the barcode of the labware to load
     * @return the labware loaded, or null if none was loaded
     */
    public Labware lookUpLabware(Collection<String> problems, String barcode) {
        if (barcode==null || barcode.isEmpty()) {
            problems.add("No barcode specified.");
            return null;
        }
        var optLw = labwareRepo.findByBarcode(barcode);
        if (optLw.isPresent()) {
            return optLw.get();
        }
        problems.add("Unknown labware barcode: "+repr(barcode));
        return null;
    }

    /**
     * Looks up the control barcode (there should be one at most).
     * @param problems receptacle for problems
     * @param permData the perm data that may include a control barcode
     * @return the labware loaded from the control barcode; or null
     */
    public Labware lookUpControlLabware(Collection<String> problems, Collection<PermData> permData) {
        String[] barcodes = permData.stream()
                .map(PermData::getControlBarcode)
                .filter(Objects::nonNull)
                .toArray(String[]::new);
        if (barcodes.length==0) {
            return null;
        }
        if (barcodes.length > 1) {
            problems.add("A control barcode was specified in multiple slots.");
            return null;
        }
        String barcode = barcodes[0];
        var optLw = labwareRepo.findByBarcode(barcode);
        if (optLw.isPresent()) {
            return optLw.get();
        }
        problems.add("Unknown control barcode: "+repr(barcode));
        return null;
    }

    /**
     * Checks the labware is usable in this operation.
     * Usable means it's a usable source (according to {@link LabwareValidator}
     * and it has had a stain op recorded on it.
     * If the labware given is null, then there is nothing to check.
     * @param problems receptacle for problems
     * @param lw the labware to validate, or null if there is no labware to validate
     */
    public void validateLabware(Collection<String> problems, Labware lw) {
        if (lw==null) {
            return;
        }
        LabwareValidator lv = labwareValidatorFactory.getValidator(List.of(lw));
        lv.validateSources();
        var valErrors = lv.getErrors();
        if (!valErrors.isEmpty()) {
            problems.addAll(valErrors);
            return;
        }
    }

    /**
     * Checks that the control labware is suitable, if given.
     * @param problems receptacle for problems
     * @param lw the control labware to validate, or null if there is no labware to validate
     */
    public void validateControlLabware(Collection<String> problems, Labware lw) {
        if (lw==null) {
            return;
        }
        LabwareValidator lv = labwareValidatorFactory.getValidator(List.of(lw));
        lv.setSingleSample(true);
        lv.validateSources();
        problems.addAll(lv.getErrors());
    }

    /**
     * Checks that the perm data is valid for the labware given.
     * Valid means that the perm data is non-null, non-empty, and has valid addresses and values
     * according to {@link #validateAddresses} and {@link #validatePermValues}.
     * If no labware is given then there is nothing to check.
     * @param problems receptacle for problems
     * @param lw the labware to validate, or null if there is no labware to validate
     * @param permData the data that the user wants to record against the given labware
     */
    public void validatePermData(Collection<String> problems, Labware lw, Collection<PermData> permData) {
        if (lw == null) {
            return;
        }
        if (permData == null || permData.isEmpty()) {
            problems.add("No permeabilisation data provided.");
            return;
        }
        validateAddresses(problems, lw, permData);
        validatePermValues(problems, permData);
    }

    /**
     * Checks that the addresses in the perm data are valid.
     * Valid means that the addresses are non-null, are unique, exist in the labware, and that the slots they
     * indicate are not empty.
     * @param problems receptacle for problems
     * @param lw the labware the perm data applies to
     * @param permData the perm data to check
     */
    public void validateAddresses(Collection<String> problems, Labware lw, Collection<PermData> permData) {
        Set<Address> seenAddresses = new HashSet<>(permData.size());
        Set<Address> repeatedAddresses = new LinkedHashSet<>();
        Set<Address> invalidAddresses = new LinkedHashSet<>();
        Set<Address> emptyAddresses = new LinkedHashSet<>();
        Set<Address> nonemptyControlBarcodeAddresses = new LinkedHashSet<>();
        boolean anyNull = false;
        for (PermData pd : permData) {
            final Address address = pd.getAddress();
            if (address ==null) {
                anyNull = true;
            } else if (!seenAddresses.add(address)) {
                repeatedAddresses.add(address);
            } else {
                var optSlot = lw.optSlot(address);
                if (optSlot.isEmpty()) {
                    invalidAddresses.add(address);
                } else if (optSlot.get().getSamples().isEmpty() && pd.getControlBarcode()==null) {
                    emptyAddresses.add(address);
                } else if (pd.getControlBarcode()!=null && !optSlot.get().getSamples().isEmpty()) {
                    nonemptyControlBarcodeAddresses.add(address);
                }
            }
        }
        if (anyNull) {
            problems.add("Missing slot address in perm data.");
        }
        if (!invalidAddresses.isEmpty()) {
            problems.add("Invalid slot address for labware "+lw.getBarcode()+": "+invalidAddresses);
        }
        if (!repeatedAddresses.isEmpty()) {
            problems.add("Repeated slot address: "+repeatedAddresses);
        }
        if (!emptyAddresses.isEmpty()) {
            problems.add("Indicated slot is empty: "+emptyAddresses);
        }
        if (!nonemptyControlBarcodeAddresses.isEmpty()) {
            problems.add("Positive control labware cannot be transferred into nonempty slot: "+nonemptyControlBarcodeAddresses);
        }
    }

    /**
     * Checks that the values in the perm data are valid.
     * Valid means that for each perm data element either a perm time (in seconds) is given, or a control type
     * is given, but not both; also it requires that any address that is given a control barcode should be
     * marked as a positive control.
     * @param problems receptacle for problems
     * @param permData the data to check
     */
    public void validatePermValues(Collection<String> problems, Collection<PermData> permData) {
        Set<Address> addressesBoth = new LinkedHashSet<>();
        Set<Address> addressesNeither = new LinkedHashSet<>();
        Set<Address> addressesWithBarcodeNotPosCon = new LinkedHashSet<>();
        for (PermData pd : permData) {
            final Address address = pd.getAddress();
            if (address !=null) {
                if (pd.getControlType() == null) {
                    if (pd.getSeconds() == null) {
                        addressesNeither.add(address);
                    }
                } else if (pd.getSeconds() != null) {
                    addressesBoth.add(address);
                }
                if (pd.getControlBarcode()!=null && pd.getControlType()!=ControlType.positive) {
                    addressesWithBarcodeNotPosCon.add(address);
                }
            }
        }
        if (!addressesBoth.isEmpty()) {
            problems.add("Control type and time specified for the same address: "+addressesBoth);
        }
        if (!addressesNeither.isEmpty()) {
            problems.add("Neither control type nor time specified for the given address: "+addressesNeither);
        }
        if (!addressesWithBarcodeNotPosCon.isEmpty()) {
            problems.add("Control barcode specified for address that is not a positive control: "+addressesWithBarcodeNotPosCon);
        }
    }

    /**
     * Records the requested operation and measurements, after validation.
     * @param user the user responsible for the operation
     * @param lw the labware involved in the operation
     * @param permData the perm data to record as measurements
     * @param controlLabware the labware specified for positive controls, if any
     * @param work the work to associate the operation with, or null
     * @return the operation recorded and the labware
     */
    public OperationResult record(User user, Labware lw, Collection<PermData> permData, Labware controlLabware, Work work) {
        OperationType opType = opTypeRepo.getByName("Visium permeabilisation");

        Operation addControlOp;
        if (controlLabware!=null) {
            Address address = permData.stream()
                    .filter(pd -> pd.getControlBarcode()!=null)
                    .map(PermData::getAddress)
                    .findAny()
                    .orElseThrow();
            addControlOp = addControl(user, controlLabware, lw, address);
        } else {
            addControlOp = null;
        }

        Operation op = opService.createOperationInPlace(opType, user, lw, null, null);
        createMeasurements(op.getId(), lw, permData);
        List<Operation> ops = List.of(op);
        if (work!=null) {
            workService.link(work, ops);
        }
        if (addControlOp!=null) {
            ops = List.of(addControlOp, op);
        }
        return new OperationResult(ops, List.of(lw));
    }

    /**
     * Adds a control from the given src labware into the destination at the specified address.
     * The source labware must contain one sample in one slot.
     * The destination labware is updated and new contents are saved.
     * An "Add control" operation is recorded to represent the transfer.
     * @param user the user responsible for the operation
     * @param src the source of the control
     * @param dst the destination to which the control must be added
     * @param address the address of the slot in the destination to add the control
     * @return the operation recorded
     */
    public Operation addControl(User user, Labware src, Labware dst, Address address) {
        OperationType opType = opTypeRepo.getByName("Add control");
        Slot srcSlot = src.getSlots()
                .stream()
                .filter(slot -> !slot.getSamples().isEmpty())
                .findAny()
                .orElseThrow();
        Sample sample = srcSlot.getSamples().get(0);
        Slot dstSlot = dst.getSlot(address);
        dstSlot.addSample(sample);
        dstSlot = slotRepo.save(dstSlot);

        Action action = new Action(null, null, srcSlot, dstSlot, sample, sample);
        return opService.createOperation(opType, user, List.of(action), null);
    }

    /**
     * Creates the measurements for the given perm data; saves them in the database.
     * @param opId the operation id to associate with the measurements
     * @param lw the labware to record measurements on
     * @param permData the perm data that will be turned into measurements
     */
    public void createMeasurements(final Integer opId, Labware lw, Collection<PermData> permData) {
        List<Measurement> measurements = permData.stream()
                .flatMap(pd -> {
                    String measurementName, measurementValue;
                    if (pd.getControlType()!=null) {
                        measurementName = "control";
                        measurementValue = pd.getControlType().name();
                    } else {
                        measurementName = "permeabilisation time";
                        measurementValue = pd.getSeconds().toString();
                    }
                    Slot slot = lw.getSlot(pd.getAddress());
                    return slot.getSamples().stream()
                            .map(sam -> new Measurement(null, measurementName, measurementValue, sam.getId(), opId, slot.getId()));
                }).collect(toList());
        measurementRepo.saveAll(measurements);
    }

}
