package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.MeasurementRepo;
import uk.ac.sanger.sccp.stan.repo.OperationCommentRepo;
import uk.ac.sanger.sccp.stan.request.*;
import uk.ac.sanger.sccp.stan.service.sanitiser.Sanitiser;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelper;
import uk.ac.sanger.sccp.stan.service.validation.ValidationHelperFactory;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;
import static uk.ac.sanger.sccp.utils.BasicUtils.pluralise;

/**
 * @author dr6
 */
@Service
public class OpWithSlotMeasurementsServiceImp implements OpWithSlotMeasurementsService {
    public static final String OP_AMP = "Amplification",
            OP_VISIUM_CONC = "Visium concentration",
            OP_QPCR = "qPCR results";
    public static final String MEAS_CQ = "Cq value", MEAS_CDNA = "cDNA concentration",
            MEAS_LIBR = "Library concentration", MEAS_CYC = "Cycles";

    private final MeasurementRepo measurementRepo;
    private final OperationCommentRepo opComRepo;
    private final Sanitiser<String> cqSanitiser,  concentrationSanitiser, cycleSanitiser;
    private final WorkService workService;
    private final OperationService opService;
    private final CommentValidationService commentValidationService;
    private final ValidationHelperFactory valHelperFactory;

    private final Map<String, List<String>> opTypeMeasurements;

    public OpWithSlotMeasurementsServiceImp(MeasurementRepo measurementRepo, OperationCommentRepo opComRepo,
                                            @Qualifier("cqSanitiser") Sanitiser<String> cqSanitiser,
                                            @Qualifier("concentrationSanitiser") Sanitiser<String> concentrationSanitiser,
                                            @Qualifier("cycleSanitiser") Sanitiser<String> cycleSanitiser,
                                            WorkService workService, OperationService opService,
                                            CommentValidationService commentValidationService,
                                            ValidationHelperFactory valHelperFactory) {
        this.measurementRepo = measurementRepo;
        this.opComRepo = opComRepo;
        this.cqSanitiser = cqSanitiser;
        this.concentrationSanitiser = concentrationSanitiser;
        this.cycleSanitiser = cycleSanitiser;
        this.workService = workService;
        this.opService = opService;
        this.commentValidationService = commentValidationService;
        this.valHelperFactory = valHelperFactory;
        UCMap<List<String>> opTypeMeasurements = new UCMap<>(3);
        opTypeMeasurements.put(OP_AMP, List.of(MEAS_CQ, MEAS_CYC));
        opTypeMeasurements.put(OP_VISIUM_CONC, List.of(MEAS_CDNA, MEAS_LIBR));
        opTypeMeasurements.put(OP_QPCR, List.of(MEAS_CQ));
        this.opTypeMeasurements = Collections.unmodifiableMap(opTypeMeasurements);
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
        ValidationHelper val = valHelperFactory.getHelper();
        Labware lw = validateLabware(val, request.getBarcode());
        OperationType opType = loadOpType(val, request.getOperationType());
        Work work = workService.validateUsableWork(problems, request.getWorkNumber());
        if (lw!=null) {
            validateAddresses(problems, lw, request.getSlotMeasurements());
        }
        List<Comment> comments = validateComments(problems, request.getSlotMeasurements());
        List<SlotMeasurementRequest> sanitisedMeasurements = sanitiseMeasurements(problems, opType, request.getSlotMeasurements());
        checkForDupeMeasurements(problems, sanitisedMeasurements);
        problems.addAll(val.getProblems());
        if (!problems.isEmpty()) {
            throw new ValidationException("The request could not be validated.", problems);
        }

        return execute(user, lw, opType, work, comments, sanitisedMeasurements);
    }

    /**
     * Loads and checks the labware from the given barcode
     * @param val validation helper to load labware and track problems
     * @param barcode the labware barcode to load
     * @return the labware loaded, if any
     */
    public Labware validateLabware(ValidationHelper val, String barcode) {
        List<String> barcodes = nullOrEmpty(barcode) ? List.of() : List.of(barcode);
        UCMap<Labware> lwMap = val.checkLabware(barcodes);
        return (lwMap.isEmpty() ? null : lwMap.values().iterator().next());
    }

    /**
     * Loads the op type and checks that it is in-place
     * @param val validation helper to load op type and track problems
     * @param opTypeName the name of the operation to record
     * @return the op type loaded
     */
    public OperationType loadOpType(ValidationHelper val, String opTypeName) {
        return val.checkOpType(opTypeName, EnumSet.of(OperationTypeFlag.IN_PLACE), null,
                ot -> opTypeMeasurements.containsKey(ot.getName()));
    }

    @Override
    public OperationType loadOpType(Collection<String> problems, String opTypeName) {
        ValidationHelper val = valHelperFactory.getHelper();
        OperationType opType = loadOpType(val, opTypeName);
        problems.addAll(val.getProblems());
        return opType;
    }

    /**
     * Checks that the addresses given in the measurement requests are present and valid.
     * @param problems receptacle for problems found
     * @param lw the labware to which the measurements refer
     * @param slotMeasurements the requested measurements
     */
    public void validateAddresses(Collection<String> problems, Labware lw, List<SlotMeasurementRequest> slotMeasurements) {
        Set<Address> filledSlotAddresses = lw.getSlots().stream()
                .filter(slot -> !slot.getSamples().isEmpty())
                .map(Slot::getAddress)
                .collect(toSet());
        validateAddresses(problems, lw.getLabwareType(), filledSlotAddresses, slotMeasurements);
    }

    @Override
    public void validateAddresses(Collection<String> problems, LabwareType lt, Set<Address> filledSlotAddresses,
                                  List<SlotMeasurementRequest> slotMeasurements) {
        Set<Address> invalidAddresses = new LinkedHashSet<>();
        Set<Address> emptyAddresses = new LinkedHashSet<>();
        boolean nullAddress = false;
        for (SlotMeasurementRequest smr : slotMeasurements) {
            final Address address = smr.getAddress();
            if (address ==null) {
                nullAddress = true;
            } else {
                if (lt.indexOf(address) < 0) {
                    invalidAddresses.add(address);
                } else if (!filledSlotAddresses.contains(address)){
                    emptyAddresses.add(address);
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

    @Override
    public List<Comment> validateComments(Collection<String> problems, Collection<SlotMeasurementRequest> sms) {
        Stream<Integer> commentIdStream = sms.stream()
                .map(SlotMeasurementRequest::getCommentId)
                .filter(Objects::nonNull);
        return commentValidationService.validateCommentIds(problems, commentIdStream);
    }

    @Override
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
        if (opType != null && name != null && smr.getAddress() != null && value !=null) {
            return smr.withNameAndValue(name, value);
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
        List<String> expectedMeasurements = opTypeMeasurements.get(opType.getName());
        if (expectedMeasurements==null) {
            return null;
        }
        return expectedMeasurements.stream().filter(name::equalsIgnoreCase).findAny().orElse(null);
    }

    /**
     * Sanitises the measurement value. This is done using a {@link Sanitiser}.
     * @param problems receptacle for problems found by the sanitiser
     * @param name the sanitised name of the measurement
     * @param value the given value
     * @return the sanitised value, or null if the measurement is found to be invalid
     */
    public String sanitiseMeasurementValue(Collection<String> problems, String name, String value) {
        return switch (name) {
            case MEAS_CDNA, MEAS_LIBR -> concentrationSanitiser.sanitise(problems, value);
            case MEAS_CQ -> cqSanitiser.sanitise(problems, value);
            case MEAS_CYC -> cycleSanitiser.sanitise(problems, value);
            default -> null;
        };
    }

    @Override
    public void checkForDupeMeasurements(Collection<String> problems, Collection<SlotMeasurementRequest> smrs) {
        if (smrs==null || smrs.size() <= 1) {
            return;
        }
        Set<SlotMeasurementRequest> seen = new HashSet<>();
        Set<SlotMeasurementRequest> repeated = new LinkedHashSet<>();
        for (SlotMeasurementRequest smr : smrs) {
            SlotMeasurementRequest key = new SlotMeasurementRequest(smr.getAddress(), smr.getName(), null, null);
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

    @Override
    public OperationResult execute(User user, Labware lw, OperationType opType, Work work,
                                   Collection<Comment> comments,
                                   Collection<SlotMeasurementRequest> sanitisedMeasurements) {
        Operation op = opService.createOperationInPlace(opType, user, lw, null, null);
        createMeasurements(op.getId(), lw, sanitisedMeasurements);
        List<Operation> ops = List.of(op);
        if (work!=null) {
            workService.link(work, ops);
        }
        if (!comments.isEmpty()) {
            recordComments(op.getId(), comments, lw, sanitisedMeasurements);
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

    /**
     * Records operation comments, if any
     * @param opId the id of the operation
     * @param sms the slot measurement requests
     */
    public void recordComments(Integer opId, Collection<Comment> comments, Labware lw,
                               Collection<SlotMeasurementRequest> sms) {
        Map<Integer, Comment> commentMap = comments.stream().collect(BasicUtils.inMap(Comment::getId));

        List<OperationComment> opComs = sms.stream()
                .filter(sm -> sm.getCommentId()!=null)
                .flatMap(sm -> newOpComs(opId, lw.getSlot(sm.getAddress()), commentMap.get(sm.getCommentId())))
                .collect(toList());
        if (!opComs.isEmpty()) {
            opComRepo.saveAll(opComs);
        }
    }

    /**
     * Returns new (unsaved) operation comments
     * @param opId operation id
     * @param slot the slot in the labware
     * @param comment the comment to record
     * @return a stream of new opcoms
     */
    private Stream<OperationComment> newOpComs(Integer opId, Slot slot, Comment comment) {
        return slot.getSamples().stream()
                .map(sam -> new OperationComment(null, comment, opId, sam.getId(), slot.getId(), null));
    }
}
