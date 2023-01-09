package uk.ac.sanger.sccp.stan.service.operation.confirm;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.confirm.*;
import uk.ac.sanger.sccp.stan.request.AddressCommentId;
import uk.ac.sanger.sccp.stan.service.OperationService;
import uk.ac.sanger.sccp.stan.service.ValidationException;

import javax.persistence.EntityManager;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.coalesce;

/**
 * @author dr6
 */
@Service
public class ConfirmOperationServiceImp implements ConfirmOperationService {
    private final ConfirmOperationValidationFactory validationFactory;
    private final EntityManager entityManager;
    private final OperationService operationService;
    private final LabwareRepo labwareRepo;
    private final PlanOperationRepo planOpRepo;
    private final SlotRepo slotRepo;
    private final SampleRepo sampleRepo;
    private final CommentRepo commentRepo;
    private final OperationCommentRepo opCommentRepo;
    private final MeasurementRepo measurementRepo;

    @Autowired
    public ConfirmOperationServiceImp(ConfirmOperationValidationFactory validationFactory, EntityManager entityManager,
                                      OperationService operationService,
                                      LabwareRepo labwareRepo, PlanOperationRepo planOpRepo, SlotRepo slotRepo,
                                      SampleRepo sampleRepo, CommentRepo commentRepo,
                                      OperationCommentRepo opCommentRepo, MeasurementRepo measurementRepo) {
        this.validationFactory = validationFactory;
        this.entityManager = entityManager;
        this.operationService = operationService;
        this.labwareRepo = labwareRepo;
        this.planOpRepo = planOpRepo;
        this.slotRepo = slotRepo;
        this.sampleRepo = sampleRepo;
        this.commentRepo = commentRepo;
        this.opCommentRepo = opCommentRepo;
        this.measurementRepo = measurementRepo;
    }

    @Override
    public ConfirmOperationResult confirmOperation(User user, ConfirmOperationRequest request) {
        ConfirmOperationValidation validation = validationFactory.createConfirmOperationValidation(request);
        Collection<String> problems = validation.validate();
        if (!problems.isEmpty()) {
            throw new ValidationException("The confirm operation request could not be validated.", problems);
        }
        return recordConfirmation(user, request);
    }

    /**
     * Performs a validated confirmation request and returns the result.
     * @param user the user performing the request
     * @param request the request
     * @return the result object
     */
    public ConfirmOperationResult recordConfirmation(User user, ConfirmOperationRequest request) {
        Map<String, Labware> labwareMap = loadLabware(
                request.getLabware().stream()
                        .map(col -> col.getBarcode().toUpperCase())
                        .collect(toSet())
        );
        Map<Integer, PlanOperation> planMap = loadPlans(labwareMap.values());
        List<Operation> resultOps = new ArrayList<>(request.getLabware().size());
        List<Labware> resultLabware = new ArrayList<>(request.getLabware().size());

        for (ConfirmOperationLabware col : request.getLabware()) {
            Labware lw = labwareMap.get(col.getBarcode().toUpperCase());
            if (lw==null) {
                throw new IllegalArgumentException("Invalid labware barcode: "+col.getBarcode());
            }
            PlanOperation plan = planMap.get(lw.getId());
            if (plan==null) {
                throw new IllegalArgumentException("No plan found for labware "+col.getBarcode());
            }
            ConfirmLabwareResult clr = performConfirmation(col, lw, plan, user);
            // Assumption:
            // when we create a new sample, that sample is not simultaneously created in several bits of labware
            //  (which might be confirmed in several operations)
            recordComments(col, clr.operation==null ? null : clr.operation.getId(), clr.labware);
            if (clr.operation!=null) {
                resultOps.add(clr.operation);
            }
            resultLabware.add(clr.labware);
        }
        return new ConfirmOperationResult(resultOps, resultLabware);
    }

    /**
     * Loads a map of barcode (upper case) to labware, looking up the given barcodes in the database
     * @param barcodes the labware barcodes to look up
     * @return a map of barcode (upper case) to labware
     */
    public Map<String, Labware> loadLabware(Collection<String> barcodes) {
        return labwareRepo.findByBarcodeIn(barcodes).stream()
                .collect(toMap(lw -> lw.getBarcode().toUpperCase(), lw -> lw));
    }

    /**
     * Loads a map of labware id to the plan for that labware.
     * @param labware the labware to look up plans for
     * @return a map of labware id to plan
     */
    public Map<Integer, PlanOperation> loadPlans(Collection<Labware> labware) {
        Set<Integer> labwareIds = labware.stream().map(Labware::getId).collect(toSet());
        List<PlanOperation> plans = planOpRepo.findAllByDestinationIdIn(labwareIds);
        Map<Integer, PlanOperation> labwarePlans = new HashMap<>(labwareIds.size());
        for (PlanOperation plan : plans) {
            for (PlanAction pa : plan.getPlanActions()) {
                Integer labwareId = pa.getDestination().getLabwareId();
                if (labwareIds.contains(labwareId)) {
                    labwarePlans.put(labwareId, plan);
                }
            }
        }
        return labwarePlans;
    }

    /**
     * Updates the labware and records the operation (if necessary) described by the given confirm-labware request.
     * If the operation is cancelled, no operation will be recorded, and the labware will be discarded.
     * Thickness measurements from the plan are recorded against the new operation (if any).
     * @param col the confirm-request for a particular piece of labware
     * @param lw the labware
     * @param plan the plan for the labware
     * @param user the user confirming the operation
     * @return a result object containing the operation created (if any) and the updated labware
     */
    public ConfirmLabwareResult performConfirmation(ConfirmOperationLabware col, Labware lw, PlanOperation plan, User user) {
        if (col.isCancelled()) {
            lw.setDiscarded(true);
            lw = labwareRepo.save(lw);
            return new ConfirmLabwareResult(null, lw);
        }
        final int labwareId = lw.getId();

        final Predicate<PlanAction> planActionFilter;
        final Set<CancelPlanAction> cpas = col.getCancelledActions();
        if (cpas!=null && !cpas.isEmpty()) {
            planActionFilter = pa -> (pa.getDestination().getLabwareId()==labwareId
                    && !cpas.contains(CancelPlanAction.forPlanAction(pa)));
        } else {
            planActionFilter = pa -> pa.getDestination().getLabwareId()==labwareId;
        }
        List<PlanAction> planActions = plan.getPlanActions().stream()
                .filter(planActionFilter)
                .collect(toList());
        if (planActions.isEmpty()) {
            // effectively whole lw is cancelled
            lw.setDiscarded(true);
            lw = labwareRepo.save(lw);
            return new ConfirmLabwareResult(null, lw);
        }
        List<Action> actions = new ArrayList<>(planActions.size());

        List<Measurement> measurements = new ArrayList<>();

        Set<Slot> slotsToSave = new HashSet<>();

        for (PlanAction pa : planActions) {
            // update the slot object inside the labware, in case we have multiple identical slot instances
            // whose updates might clobber each other
            Slot dest = lw.getSlot(pa.getDestination().getAddress());
            Sample sample = getOrCreateSample(pa, dest);
            Action action = makeAction(pa, dest, sample);
            actions.add(action);
            dest.getSamples().add(sample);
            slotsToSave.add(dest);
            if (pa.getSampleThickness()!=null) {
                measurements.add(new Measurement(null, "Thickness", String.valueOf(pa.getSampleThickness()),
                        sample.getId(), null, dest.getId()));
            }
        }
        slotRepo.saveAll(slotsToSave);
        entityManager.refresh(lw);
        Operation op = operationService.createOperation(plan.getOperationType(), user, actions, plan.getId());
        if (!measurements.isEmpty()) {
            measurements.forEach(m -> m.setOperationId(op.getId()));
            measurementRepo.saveAll(measurements);
        }
        return new ConfirmLabwareResult(op, lw);
    }

    /**
     * Gets the sample needed for the given plan action.
     * If the source sample is suitable, returns that.
     * Otherwise, if the slot already contains a suitable sample, returns that.
     * Otherwise, creates a new sample (in the database) and returns it.
     * @param planAction the plan action responsible for putting a sample into the given slot
     * @param dest a slot that will be receiving the sample
     * @return a new or existing sample
     */
    public Sample getOrCreateSample(PlanAction planAction, Slot dest) {
        final Sample sourceSample = planAction.getSample();
        final Integer correctSection = coalesce(planAction.getNewSection(), sourceSample.getSection());
        final BioState correctBioState = coalesce(planAction.getNewBioState(), sourceSample.getBioState());
        if (Objects.equals(correctSection, sourceSample.getSection())
                && correctBioState.getId().equals(sourceSample.getBioState().getId())) {
            return sourceSample;
        }
        return dest.getSamples().stream()
                .filter(sample -> sample.getTissue().getId().equals(sourceSample.getTissue().getId())
                        && Objects.equals(sample.getSection(), correctSection)
                        && correctBioState.getId().equals(sample.getBioState().getId()))
                .findFirst()
                .orElseGet(() -> createNewSample(sourceSample, correctSection, correctBioState));
    }

    /**
     * Records the comments (if any) indicated in the given confirm-labware request.
     * @param col the confirm-labware request
     * @param operationId the operation id (if any) the comments will be associated with
     * @param labware the indicated labware
     */
    public void recordComments(ConfirmOperationLabware col, Integer operationId, Labware labware) {
        if (col.getAddressComments()==null || col.getAddressComments().isEmpty()) {
            return;
        }

        Set<Integer> commentIdSet = col.getAddressComments().stream()
                .map(AddressCommentId::getCommentId)
                .collect(toSet());
        Map<Integer, Comment> commentIdMap = commentRepo.findAllByIdIn(commentIdSet).stream()
                .collect(toMap(Comment::getId, cmt -> cmt));
        if (commentIdMap.size() < commentIdSet.size()) {
            List<Integer> missing = commentIdSet.stream()
                    .filter(cmtId -> !commentIdMap.containsKey(cmtId))
                    .collect(toList());
            throw new IllegalArgumentException("Invalid comment ids: "+missing);
        }
        List<OperationComment> opComments = col.getAddressComments().stream()
                .distinct()
                .flatMap(ac -> streamOperationComments(ac, commentIdMap, labware, operationId))
                .collect(toList());

        opCommentRepo.saveAll(opComments);
    }

    private Stream<OperationComment> streamOperationComments(AddressCommentId ac, Map<Integer, Comment> commentIdMap,
                                                             Labware labware, Integer operationId) {
        Slot slot = labware.getSlot(ac.getAddress());
        final Integer slotId = slot.getId();
        List<Sample> samples = slot.getSamples();
        Stream<Integer> sampleIdStream;
        if (samples==null || samples.isEmpty()) {
            sampleIdStream = Stream.of((Integer) null);
        } else {
            sampleIdStream = samples.stream().map(Sample::getId);
        }
        final Comment comment = commentIdMap.get(ac.getCommentId());
        return sampleIdStream.map(sampleId -> new OperationComment(null, comment, operationId, sampleId, slotId, null));
    }

    private Action makeAction(PlanAction planAction, Slot destination, Sample sample) {
        return new Action(null, null, planAction.getSource(), destination, sample, planAction.getSample());
    }

    private Sample createNewSample(Sample sourceSample, Integer section, BioState bioState) {
        return sampleRepo.save(new Sample(null, section, sourceSample.getTissue(), bioState));
    }

    static class ConfirmLabwareResult {
        Operation operation;
        Labware labware;

        public ConfirmLabwareResult(Operation operation, Labware labware) {
            this.operation = operation;
            this.labware = labware;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ConfirmLabwareResult that = (ConfirmLabwareResult) o;
            return (Objects.equals(this.operation, that.operation)
                    && Objects.equals(this.labware, that.labware));
        }

        @Override
        public int hashCode() {
            return Objects.hash(operation, labware);
        }
    }
}
