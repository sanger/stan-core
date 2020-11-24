package uk.ac.sanger.sccp.stan.service.operation.plan;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.plan.*;
import uk.ac.sanger.sccp.stan.service.Validator;

import java.util.*;
import java.util.stream.Stream;

/**
 * @author dr6
 */
public class PlanValidationImp implements PlanValidation {
    private final LabwareRepo labwareRepo;
    private final LabwareTypeRepo ltRepo;
    private final OperationTypeRepo opTypeRepo;
    private final Validator<String> prebarcodeValidator;

    private final Set<String> problems = new LinkedHashSet<>();
    private final PlanRequest request;

    public PlanValidationImp(PlanRequest request, LabwareRepo labwareRepo, LabwareTypeRepo ltRepo,
                             OperationTypeRepo opTypeRepo, Validator<String> prebarcodeValidator) {
        this.labwareRepo = labwareRepo;
        this.ltRepo = ltRepo;
        this.opTypeRepo = opTypeRepo;
        this.request = request;
        this.prebarcodeValidator = prebarcodeValidator;
    }

    @Override
    public Collection<String> validate() {
        OperationType opType = validateOperation();
        validateSources(opType);
        validateDestinations();
        return problems;
    }

    public void validateSources(OperationType opType) {
        if (request.getLabware().isEmpty()) {
            return;
        }
        Map<String, Labware> labwareMap = new HashMap<>();
        Set<String> unfoundBarcodes = new LinkedHashSet<>();
        for (PlanRequestAction action : (Iterable<PlanRequestAction>) (actions()::iterator)) {
            PlanRequestSource source = action.getSource();
            if (source==null || source.getBarcode()==null || source.getBarcode().isEmpty()) {
                addProblem("Missing source barcode.");
                continue;
            }
            String barcode = source.getBarcode().toUpperCase();
            if (unfoundBarcodes.contains(barcode)) {
                continue;
            }
            Labware lw = labwareMap.get(barcode);
            if (lw==null) {
                Optional<Labware> optLw = labwareRepo.findByBarcode(barcode);
                if (optLw.isEmpty()) {
                    unfoundBarcodes.add(barcode);
                    continue;
                }
                lw = optLw.get();
                labwareMap.put(barcode, lw);
            }
            Address address = (source.getAddress()==null ? new Address(1,1) : source.getAddress());
            if (lw.getLabwareType().indexOf(address) < 0) {
                addProblem("Labware %s (%s) has no slot at address %s.", barcode, lw.getLabwareType().getName(), address);
                continue;
            }
            Slot slot = lw.getSlot(address);
            if (slot.getSamples().stream().noneMatch(sample -> sample.getId()==action.getSampleId())) {
                addProblem("Slot %s of labware %s does not contain a sample with ID %s.",
                        address, barcode, action.getSampleId());
            }
            if (opType!=null && opType.sourceMustBeBlock() && !slot.isBlock()) {
                addProblem("Source %s is not a block for operation %s.", action.getSource(), opType.getName());
            }
        }
        if (!unfoundBarcodes.isEmpty()) {
            addProblem("Unknown labware barcode%s: %s", unfoundBarcodes.size()==1 ? "" : "s", unfoundBarcodes);
        }
    }

    public void validateDestinations() {
        if (request.getLabware().isEmpty()) {
            addProblem("No labware are specified in the plan request.");
            return;
        }
        Map<String, LabwareType> labwareTypeMap = new HashMap<>();
        Set<String> unknownTypes = new LinkedHashSet<>();
        Set<String> seenBarcodes = new HashSet<>();
        for (PlanRequestLabware lw : request.getLabware()) {
            boolean gotBarcode = (lw.getBarcode()!=null && !lw.getBarcode().isEmpty());
            boolean alreadySeen = false;
            if (gotBarcode) {
                if (!seenBarcodes.add(lw.getBarcode().toUpperCase())) {
                    addProblem("Repeated barcode given for new labware: " + lw.getBarcode());
                    alreadySeen = true;
                } else {
                    prebarcodeValidator.validate(lw.getBarcode(), this::addProblem);
                }
            }

            if (lw.getLabwareType() ==null || lw.getLabwareType().isEmpty()) {
                addProblem("Missing labware type.");
                continue;
            }
            String ltn = lw.getLabwareType().toUpperCase();
            LabwareType lt = labwareTypeMap.get(ltn);
            if (lt==null) {
                if (unknownTypes.contains(ltn)) {
                    continue;
                }
                Optional<LabwareType> optLt = ltRepo.findByName(ltn);
                if (optLt.isEmpty()) {
                    unknownTypes.add(ltn);
                    continue;
                }
                lt = optLt.get();
                labwareTypeMap.put(ltn, lt);
            }
            if (gotBarcode != lt.isPrebarcoded()) {
                addProblem("%s barcode supplied for new labware of type %s.",
                        gotBarcode ? "Unexpected":"No", lt.getName());
            }
            checkActions(lw, lt);
            if (gotBarcode && !alreadySeen && labwareRepo.existsByBarcode(lw.getBarcode())) {
                addProblem("Labware with the barcode "+lw.getBarcode()+" already exists in the database.");
            }
        }
        if (!unknownTypes.isEmpty()) {
            addProblem("Unknown labware type%s: %s", unknownTypes.size()==1 ? "" : "s", unknownTypes);
        }
    }

    public OperationType validateOperation() {
        if (request.getOperationType()==null || request.getOperationType().isEmpty()) {
            addProblem("No operation type specified.");
            return null;
        }
        Optional<OperationType> optOpType = opTypeRepo.findByName(request.getOperationType());
        if (optOpType.isEmpty()) {
            addProblem("Unknown operation type: "+request.getOperationType());
            return null;
        }
        OperationType opType = optOpType.get();
        if (!opType.canPrelabel()) {
            addProblem("You cannot prelabel for operation type %s.", opType.getName());
        }
        return opType;
    }

    public void checkActions(PlanRequestLabware lw, LabwareType lt) {
        if (lw.getActions().isEmpty()) {
            addProblem("No actions specified for labware %s.", lwErrorDesc(lw));
            return;
        }
        Set<ActionKey> keys = new HashSet<>(lw.getActions().size());
        for (PlanRequestAction ac : lw.getActions()) {
            if (ac.getAddress()==null) {
                addProblem("Missing destination address.");
                continue;
            }
            if (lt!=null && lt.indexOf(ac.getAddress()) < 0) {
                addProblem("Invalid address %s given for labware type %s.", ac.getAddress(), lt.getName());
            }
            ActionKey key = new ActionKey(ac);
            if (key.isComplete() && !keys.add(key)) {
                addProblem("Actions for labware %s contains duplicate action: %s", lwErrorDesc(lw), key);
            }
        }
    }

    private static String lwErrorDesc(PlanRequestLabware lw) {
        if (lw.getBarcode()!=null && !lw.getBarcode().isEmpty()) {
            return lw.getBarcode();
        }
        if (lw.getLabwareType()!=null && !lw.getLabwareType().isEmpty()) {
            return "of type "+lw.getLabwareType();
        }
        return "of unspecified type";
    }

    private Stream<PlanRequestAction> actions() {
        return request.getLabware().stream().flatMap(rl -> rl.getActions().stream());
    }

    private void addProblem(String problem) {
        problems.add(problem);
    }

    private void addProblem(String format, Object... args) {
        addProblem(String.format(format, args));
    }

    private static class ActionKey {
        String sourceBarcode;
        Address sourceAddress;
        int sampleId;
        Address destAddress;

        ActionKey(PlanRequestAction action) {
            this(action.getSource().getBarcode(), action.getSource().getAddress(),
                    action.getSampleId(), action.getAddress());
        }

        ActionKey(String sourceBarcode, Address sourceAddress, int sampleId, Address destAddress) {
            this.sourceBarcode = (sourceBarcode==null ? null : sourceBarcode.toUpperCase());
            this.sourceAddress = (sourceAddress==null ? new Address(1,1) : sourceAddress);
            this.sampleId = sampleId;
            this.destAddress = destAddress;
        }

        boolean isComplete() {
            return (this.sourceAddress!=null && this.sourceBarcode!=null && this.destAddress!=null);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ActionKey that = (ActionKey) o;
            return (this.sampleId == that.sampleId
                    && Objects.equals(this.sourceBarcode, that.sourceBarcode)
                    && Objects.equals(this.sourceAddress, that.sourceAddress)
                    && Objects.equals(this.destAddress, that.destAddress));
        }

        @Override
        public int hashCode() {
            return Objects.hash(sourceBarcode, sourceAddress, sampleId, destAddress);
        }

        @Override
        public String toString() {
            return String.format("(address=%s, sampleId=%s, source={%s, %s})",
                    destAddress, sampleId, sourceBarcode, sourceAddress);
        }
    }
}
