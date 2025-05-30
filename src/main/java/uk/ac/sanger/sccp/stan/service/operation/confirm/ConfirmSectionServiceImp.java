package uk.ac.sanger.sccp.stan.service.operation.confirm;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.confirm.*;
import uk.ac.sanger.sccp.stan.request.confirm.ConfirmSectionLabware.AddressCommentId;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import javax.persistence.EntityManager;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;
import static org.hibernate.internal.util.NullnessHelper.coalesce;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * @author dr6
 */
@Service
public class ConfirmSectionServiceImp implements ConfirmSectionService {
    private final ConfirmSectionValidationService validationService;
    private final BioRiskService bioRiskService;
    private final OperationService opService;
    private final WorkService workService;
    private final LabwareRepo lwRepo;
    private final SlotRepo slotRepo;
    private final MeasurementRepo measurementRepo;
    private final SampleRepo sampleRepo;
    private final CommentRepo commentRepo;
    private final OperationCommentRepo opCommentRepo;
    private final LabwareNoteRepo lwNoteRepo;
    private final SamplePositionRepo samplePositionRepo;

    private final EntityManager entityManager;

    @Autowired
    public ConfirmSectionServiceImp(ConfirmSectionValidationService validationService, BioRiskService bioRiskService,
                                    OperationService opService, WorkService workService,
                                    LabwareRepo lwRepo, SlotRepo slotRepo, MeasurementRepo measurementRepo,
                                    SampleRepo sampleRepo, CommentRepo commentRepo, OperationCommentRepo opCommentRepo,
                                    LabwareNoteRepo lwNoteRepo, SamplePositionRepo samplePositionRepo,
                                    EntityManager entityManager) {
        this.validationService = validationService;
        this.bioRiskService = bioRiskService;
        this.opService = opService;
        this.workService = workService;
        this.lwRepo = lwRepo;
        this.slotRepo = slotRepo;
        this.measurementRepo = measurementRepo;
        this.sampleRepo = sampleRepo;
        this.commentRepo = commentRepo;
        this.opCommentRepo = opCommentRepo;
        this.lwNoteRepo = lwNoteRepo;
        this.samplePositionRepo = samplePositionRepo;
        this.entityManager = entityManager;
    }

    @Override
    public OperationResult confirmOperation(User user, ConfirmSectionRequest request) throws ValidationException {
        ConfirmSectionValidation validation = validationService.validate(request);
        if (!validation.getProblems().isEmpty()) {
            throw new ValidationException("The operation could not be validated.", validation.getProblems());
        }

        return perform(user, validation, request);
    }

    /**
     * Performs the request operation, which has already been validated.
     * @param user the user responsible for the request
     * @param validation the result of the validation
     * @param request the request
     * @return the result of the request: operations that were created and labware that was populated
     */
    public OperationResult perform(User user, ConfirmSectionValidation validation, ConfirmSectionRequest request) {
        UCMap<Labware> labwareMap = validation.getLabware();
        Map<Integer, PlanOperation> lwPlans = validation.getLwPlans();
        final int nlw = request.getLabware().size();
        List<Operation> operations = new ArrayList<>(nlw);
        List<Labware> resultLabware = new ArrayList<>(nlw);
        Set<Integer> planIds = lwPlans.values().stream()
                .map(PlanOperation::getId)
                .collect(toSet());
        var plansNotes = loadPlanNotes(planIds);
        Map<Operation, String> opWork = new HashMap<>(request.getLabware().size());
        for (ConfirmSectionLabware csl : request.getLabware()) {
            Labware lw = labwareMap.get(csl.getBarcode());
            if (lw==null) {
                throw new IllegalArgumentException("Invalid labware barcode: " + csl.getBarcode());
            }
            PlanOperation plan = lwPlans.get(lw.getId());
            if (plan==null) {
                throw new IllegalArgumentException("No plan found for labware " + lw.getBarcode());
            }
            ConfirmLabwareResult clr = confirmLabware(user, csl, lw, plan, validation.getSlotRegions(), validation.getComments());
            var notes = plansNotes.get(plan.getId());
            if (notes!=null && !notes.isEmpty()) {
                updateNotes(notes, clr.operation.getId(), lw.getId());
            }
            // Assumption:
            // when we create a new sample, that sample is not simultaneously created in several bits of labware
            //  (which might be confirmed in several operations)
            recordAddressComments(csl, clr.operation==null ? null : clr.operation.getId(), clr.labware);
            if (clr.operation!=null) {
                operations.add(clr.operation);
                if (!nullOrEmpty(csl.getWorkNumber())) {
                    opWork.put(clr.operation, csl.getWorkNumber());
                }
            }
            resultLabware.add(clr.labware);
        }
        linkWorks(opWork, validation.getWorks());
        bioRiskService.copyOpSampleBioRisks(operations);
        updateSourceBlocks(operations);
        return new OperationResult(operations, resultLabware);
    }

    /**
     * Links ops to the specified works
     * @param opWork map of op to work number
     * @param works map of work number to work
     */
    public void linkWorks(Map<Operation, String> opWork, UCMap<Work> works) {
        Map<Work, List<Operation>> workOps = new HashMap<>();
        opWork.forEach((op, wn) -> {
            Work work = works.get(wn);
            workOps.computeIfAbsent(work, k -> new ArrayList<>()).add(op);
        });
        workOps.forEach(workService::link);
    }

    /**
     * Loads the labware notes for the given plan ids.
     * @param planIds the labware notes to look up
     * @return a map from plan id to list of applicable labware notes
     */
    public Map<Integer, List<LabwareNote>> loadPlanNotes(Collection<Integer> planIds) {
        List<LabwareNote> notes = lwNoteRepo.findAllByPlanIdIn(planIds);
        return (notes.isEmpty() ? Map.of() : notes.stream().collect(groupingBy(LabwareNote::getPlanId)));
    }

    /**
     * Updates the labware notes to have the given operation id
     * @param notes the notes to update
     * @param opId the operation id
     * @param labwareId the labware id for notes being updated
     */
    public void updateNotes(Collection<LabwareNote> notes, Integer opId, Integer labwareId) {
        List<LabwareNote> newNotes = notes.stream()
                .filter(note -> labwareId.equals(note.getLabwareId()))
                .peek(note -> note.setOperationId(opId))
                .collect(toList());
        if (!newNotes.isEmpty()) {
            lwNoteRepo.saveAll(newNotes);
        }
    }

    /**
     * Performs the specified operation on the given labware
     * @param user the user responsible
     * @param csl the request pertaining to a particular item of labware
     * @param lw the item of labware
     * @param plan the plan for that item of labware
     * @return an operation (if one was created) and the labware (updated if necessary)
     */
    public ConfirmLabwareResult confirmLabware(User user, ConfirmSectionLabware csl, Labware lw, PlanOperation plan,
                                               UCMap<SlotRegion> slotRegions, Map<Integer, Comment> commentIdMap) {
        var secs = csl.getConfirmSections();
        if (csl.isCancelled() || secs==null || secs.isEmpty()) {
            lw.setDiscarded(true);
            lw = lwRepo.save(lw);
            return new ConfirmLabwareResult(null, lw);
        }

        final int lwId = lw.getId();

        Set<Slot> slotsToSave = new HashSet<>();
        List<Measurement> measurements = new ArrayList<>();
        List<SamplePosition> samplePositions = new ArrayList<>();
        List<OperationComment> opComs = new ArrayList<>();

        var planActionMap = getPlanActionMap(plan.getPlanActions(), lwId);
        List<Action> actions = new ArrayList<>(secs.size());
        for (ConfirmSection sec : secs) {
            PlanAction pa = planActionMap.get(new PlanActionKey(sec.getDestinationAddress(), sec.getSampleId()));
            if (pa==null) {
                throw new IllegalArgumentException(String.format("No plan action found matching section request: " +
                        "sample id %s in %s slot %s.", sec.getSampleId(), lw.getBarcode(), sec.getDestinationAddress()));
            }
            Slot slot = lw.getSlot(sec.getDestinationAddress()); // Use the lw object as the authoritative container for
            // slot objects that we might do multiple updates on
            Action action = makeAction(sec, pa, slot);
            final Sample sample = action.getSample();
            slot.getSamples().add(sample);
            slotsToSave.add(slot);
            if (pa.getSampleThickness()!=null) {
                measurements.add(new Measurement(null, "Thickness", String.valueOf(pa.getSampleThickness()),
                        sample.getId(), null, slot.getId()));
            }
            if (!nullOrEmpty(sec.getRegion())) {
                samplePositions.add(new SamplePosition(slot.getId(), sample.getId(), slotRegions.get(sec.getRegion()), null));
            }
            if (!nullOrEmpty(sec.getCommentIds())) {
                for (Integer commentId : sec.getCommentIds()) {
                    opComs.add(new OperationComment(null, commentIdMap.get(commentId), null, sample.getId(), slot.getId(), null));
                }
            }
            actions.add(action);
        }
        slotRepo.saveAll(slotsToSave);
        entityManager.refresh(lw);
        Operation op = opService.createOperation(plan.getOperationType(), user, actions, plan.getId());
        if (!measurements.isEmpty()) {
            measurements.forEach(m -> m.setOperationId(op.getId()));
            measurementRepo.saveAll(measurements);
        }
        if (!samplePositions.isEmpty()) {
            samplePositions.forEach(sp -> sp.setOperationId(op.getId()));
            samplePositionRepo.saveAll(samplePositions);
        }
        if (!opComs.isEmpty()) {
            opComs.forEach(oc -> oc.setOperationId(op.getId()));
            opCommentRepo.saveAll(opComs);
        }
        return new ConfirmLabwareResult(op, lw);
    }

    /**
     * Makes a new (unsaved) action representing the sectioning of a block into a new slot
     * @param sec the request pertaining to a single section
     * @param pa the plan action that specified the sample being put into the new slot
     * @param slot the destination of the new section
     * @return a new unsaved action, containing the new section
     */
    public Action makeAction(ConfirmSection sec, PlanAction pa, Slot slot) {
        Sample sample = getSection(sec, pa, slot);
        return new Action(null, null, pa.getSource(), slot, sample, pa.getSample());
    }

    /**
     * Gets the required section. If it is already present in the slot (it should not be), then it is
     * returned; otherwise it is created in the database and returned
     * @param sec the request pertaining to a single section
     * @param pa the plan action for the source sample going into this slot
     * @param slot the destination slot
     * @return the sample as specified
     */
    public Sample getSection(ConfirmSection sec, PlanAction pa, Slot slot) {
        Sample sourceSample = pa.getSample();
        Integer section = sec.getNewSection();
        BioState bs = coalesce(pa.getNewBioState(), sourceSample.getBioState());
        return slot.getSamples().stream()
                .filter(sample -> sample.getTissue().getId().equals(sourceSample.getTissue().getId())
                        && Objects.equals(sample.getSection(), section)
                        && bs.getId().equals(sample.getBioState().getId()))
                .findFirst()
                .orElseGet(() -> createSection(sourceSample, section, bs));
    }

    /**
     * Creates a new section with the given section number and bio state.
     * @param sourceSample the original sample (i.e. a block)
     * @param section the section number for the new sample
     * @param bs the biostate for the new sample
     * @return a new sample which has been created in the database
     */
    public Sample createSection(Sample sourceSample, Integer section, BioState bs) {
        return sampleRepo.save(new Sample(null, section, sourceSample.getTissue(), bs));
    }

    /**
     * Records the comments against a piece of labware
     * @param csl the request pertaining to one item of labware
     * @param opId the id of the operation we just recorded on the labware (if any)
     * @param labware the relevant item of labware
     */
    public void recordAddressComments(ConfirmSectionLabware csl, Integer opId, Labware labware) {
        if (nullOrEmpty(csl.getAddressComments())) {
            return;
        }
        Set<Integer> commentIdSet = csl.getAddressComments().stream()
                .map(AddressCommentId::getCommentId)
                .collect(toSet());
        Map<Integer, Comment> commentIdMap = commentRepo.findAllByIdIn(commentIdSet).stream()
                .collect(toMap(Comment::getId, Function.identity()));
        if (commentIdMap.size() < commentIdSet.size()) {
            List<Integer> missing = commentIdSet.stream()
                    .filter(cmtId -> !commentIdMap.containsKey(cmtId))
                    .toList();
            throw new IllegalArgumentException("Invalid comment ids: "+missing);
        }
        List<OperationComment> opComments = csl.getAddressComments().stream()
                .distinct()
                .flatMap(ac -> streamOpComments(ac, commentIdMap, labware, opId))
                .collect(toList());
        opCommentRepo.saveAll(opComments);
    }

    /**
     * Streams new (unsaved) OperationComment objects from a given comment request.
     * If the indicated slot is empty, one comment will be recorded (linked to no sample id).
     * If the indicated slot is not empty, a comment will be recorded for each sample the slot contains.
     * @param ac the request linking the comment id to an address in the labware
     * @param commentIdMap the map to look up a comment by its id
     * @param lw the labware being commented
     * @param opId the operation id we just recorded on the labware (if any)
     * @return a stream of new unsaved operation comments
     */
    private Stream<OperationComment> streamOpComments(AddressCommentId ac, Map<Integer, Comment> commentIdMap,
                                                      Labware lw, Integer opId) {
        Slot slot = lw.getSlot(ac.getAddress());
        final Integer slotId = slot.getId();
        List<Sample> samples = slot.getSamples();
        Stream<Integer> sampleIdStream;
        if (nullOrEmpty(samples)) {
            sampleIdStream = Stream.of((Integer) null);
        } else {
            sampleIdStream = samples.stream().map(Sample::getId);
        }
        final Comment comment = commentIdMap.get(ac.getCommentId());
        return sampleIdStream.map(sampleId -> new OperationComment(null, comment, opId, sampleId, slotId, null));
    }

    /**
     * Puts plan actions into a map from a {@code PlanActionKey}.
     * Only those linked to the indicated labware will be included.
     * @param planActions the plan actions to put into a map
     * @param lwId the id of the labware whose plan actions we want to include
     * @return a map from {@code PlanActionKey} to {@code PlanAction}
     */
    public Map<PlanActionKey, PlanAction> getPlanActionMap(Collection<PlanAction> planActions, final int lwId) {
        Map<PlanActionKey, PlanAction> planActionMap = new HashMap<>(planActions.size());
        for (PlanAction pa : planActions) {
            if (pa.getDestination().getLabwareId()==lwId) {
                planActionMap.putIfAbsent(new PlanActionKey(pa), pa);
            }
        }
        return planActionMap;
    }

    /**
     * Updates the highest section number for blocks following the creation of new sections from them
     * @param ops the operations creating the new sections
     */
    public void updateSourceBlocks(Collection<Operation> ops) {
        Map<Integer, Slot> slotsToUpdate = new HashMap<>();
        for (Operation op : ops) {
            for (Action action : op.getActions()) {
                Slot src = action.getSource();
                Sample sample = action.getSample();
                if (src.isBlock() && sample.getSection() != null) {
                    Integer alreadyHighestSection = src.getBlockHighestSection();
                    Slot slotInMap = slotsToUpdate.get(src.getId());
                    if (slotInMap!=null) {
                        if (alreadyHighestSection==null || slotInMap.getBlockHighestSection() > alreadyHighestSection) {
                            alreadyHighestSection = slotInMap.getBlockHighestSection();
                        }
                    }
                    if (alreadyHighestSection == null || alreadyHighestSection < sample.getSection()) {
                        src.setBlockHighestSection(sample.getSection());
                        slotsToUpdate.put(src.getId(), src);
                    }
                }
            }
        }
        slotRepo.saveAll(slotsToUpdate.values());
    }

    /** A key used to identify a particular plan action so we can look it up. */
    public record PlanActionKey(Address address, Integer sectionId) {
        PlanActionKey(PlanAction pa) {
            this(pa.getDestination().getAddress(), pa.getSample().getId());
        }
    }

    /**
     * The result on an individual piece of labware of the confirmation request.
     * If the planned operation was cancelled, then the operation included will be null.
     */
    public record ConfirmLabwareResult(Operation operation, Labware labware) {}
}
