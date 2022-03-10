package uk.ac.sanger.sccp.stan.service.operation.plan;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.plan.*;
import uk.ac.sanger.sccp.stan.service.Validator;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.stream.Stream;

/**
 * @author dr6
 */
public class PlanValidationImp implements PlanValidation {
    final LabwareRepo labwareRepo;
    final LabwareTypeRepo ltRepo;
    final OperationTypeRepo opTypeRepo;
    final Validator<String> prebarcodeValidator;

    final PlanRequest request;
    final Set<String> problems = new LinkedHashSet<>();

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
        UCMap<Labware> sourceLwMap = validateSources(opType);
        validateDestinations(sourceLwMap);
        return problems;
    }

    /**
     * Checks the sources. Returns a map of {@link ActionKey} to source slot for each action.
     * @param opType the type of operation
     */
    public UCMap<Labware> validateSources(OperationType opType) {
        UCMap<Labware> labwareMap = new UCMap<>();
        if (request.getLabware().isEmpty()) {
            return labwareMap;
        }
        Set<String> unfoundBarcodes = new LinkedHashSet<>();
        Set<String> destroyedBarcodes = new LinkedHashSet<>();
        Set<String> releasedBarcodes = new LinkedHashSet<>();
        Set<String> discardedBarcodes = new LinkedHashSet<>();
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
                if (lw.isDestroyed()) {
                    destroyedBarcodes.add(lw.getBarcode());
                } else if (lw.isReleased()) {
                    releasedBarcodes.add(lw.getBarcode());
                } else if (lw.isDiscarded()) {
                    discardedBarcodes.add(lw.getBarcode());
                }
            }
            Address address = (source.getAddress()==null ? new Address(1,1) : source.getAddress());
            if (lw.getLabwareType().indexOf(address) < 0) {
                addProblem("Labware %s (%s) has no slot at address %s.", barcode, lw.getLabwareType().getName(), address);
                continue;
            }
            Slot slot = lw.getSlot(address);
            Sample sample = slot.getSamples().stream().filter(sam -> sam.getId()==action.getSampleId())
                    .findAny().orElse(null);
            if (sample==null) {
                addProblem("Slot %s of labware %s does not contain a sample with ID %s.",
                        address, barcode, action.getSampleId());
            }
            if (opType!=null && opType.sourceMustBeBlock() && !slot.isBlock()) {
                addProblem("Source %s,%s is not a block for operation %s.", action.getSource().getBarcode(), address,
                        opType.getName());
            } else if (opType!=null && !opType.canCreateSection() && sample!=null && sample.getSection()==null) {
                addProblem("Operation %s cannot create a section of sample %s.", opType.getName(), sample.getId());
            }
        }
        if (!unfoundBarcodes.isEmpty()) {
            addProblem("Unknown labware barcode%s: %s", unfoundBarcodes.size()==1 ? "" : "s", unfoundBarcodes);
        }
        if (!releasedBarcodes.isEmpty()) {
            addProblem("Labware already released: "+releasedBarcodes);
        }
        if (!destroyedBarcodes.isEmpty()) {
            addProblem("Labware already destroyed: "+destroyedBarcodes);
        }
        if (!discardedBarcodes.isEmpty()) {
            addProblem("Labware already discarded: "+discardedBarcodes);
        }
        return labwareMap;
    }

    public void validateDestinations(UCMap<Labware> sourceLabwareMap) {
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
                        gotBarcode ? "Unexpected": "No", lt.getName());
            }
            checkActions(lw, lt);
            if (gotBarcode && !alreadySeen && labwareRepo.existsByBarcode(lw.getBarcode())) {
                addProblem("Labware with the barcode "+lw.getBarcode()+" already exists in the database.");
            } else if (gotBarcode && !alreadySeen && labwareRepo.existsByExternalBarcode(lw.getBarcode())) {
                addProblem("Labware with the external barcode "+lw.getBarcode()+" already exists in the database.");
            }
            if (lt.getLabelType()!=null && lt.getLabelType().getName().equalsIgnoreCase("adh")) {
                if (!hasDividedLayout(sourceLabwareMap, lw, lt, 1)) {
                    addProblem("Labware of type "+lt.getName()+" must have one tissue per row.");
                }
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
                // We allow duplicate actions from a block, because we can create multiple sections
                addProblem("Actions for labware %s contain duplicate action: %s", lwErrorDesc(lw), key);
            }
        }
    }

    public boolean hasDividedLayout(UCMap<Labware> sourceLwMap, PlanRequestLabware lw, LabwareType lt, int rowsPerGroup) {
        final int numGroups = lt.getNumRows() / rowsPerGroup;
        Tissue[] tissues = new Tissue[numGroups];
        for (var pa : lw.getActions()) {
            if (pa.getAddress()==null || pa.getSource()==null || pa.getSource().getBarcode()==null) {
                continue;
            }
            int tissueIndex = (pa.getAddress().getRow()-1)/rowsPerGroup;
            if (tissueIndex < 0 || tissueIndex >= numGroups) {
                continue; // must be invalid address, which is handled elsewhere
            }
            Labware sourceLabware = sourceLwMap.get(pa.getSource().getBarcode());
            if (sourceLabware==null) {
                continue;
            }
            Address sourceAddress = pa.getSource().getAddress();
            if (sourceAddress==null) {
                sourceAddress = new Address(1,1);
            }
            Slot sourceSlot = sourceLabware.optSlot(sourceAddress).orElse(null);
            if (sourceSlot==null) {
                continue;
            }
            Sample sample = sourceSlot.getSamples().stream()
                    .filter(sam -> sam.getId()==pa.getSampleId())
                    .findAny().orElse(null);
            if (sample==null) {
                continue;
            }
            Tissue tissue = sample.getTissue();
            if (tissues[tissueIndex]==null) {
                tissues[tissueIndex] = tissue;
            } else if (!tissues[tissueIndex].equals(tissue)) {
                return false;
            }
        }
        return true;
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

    static class ActionKey {
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
