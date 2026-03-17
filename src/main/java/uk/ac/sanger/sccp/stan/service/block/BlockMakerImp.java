package uk.ac.sanger.sccp.stan.service.block;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.TissueBlockRequest;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.work.WorkService;

import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.iter;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * @author dr6
 */
public class BlockMakerImp implements BlockMaker {
    private final TissueRepo tissueRepo;
    private final SampleRepo sampleRepo;
    private final SlotRepo slotRepo;
    private final LabwareRepo lwRepo;
    private final OperationCommentRepo opcomRepo;
    private final LabwareService lwService;
    private final OperationService opService;
    private final WorkService workService;
    private final BioRiskService bioRiskService;

    private final TissueBlockRequest request;
    private final List<BlockLabwareData> lwData;
    private final Medium medium;
    private final BioState bioState;
    private final Work work;
    private final OperationType opType;
    private final User user;

    public BlockMakerImp(TissueRepo tissueRepo, SampleRepo sampleRepo, SlotRepo slotRepo, LabwareRepo lwRepo,
                         OperationCommentRepo opcomRepo,
                         LabwareService lwService, OperationService opService, WorkService workService,
                         BioRiskService bioRiskService,
                         TissueBlockRequest request, List<BlockLabwareData> lwData,
                         Medium medium, BioState bioState, Work work, OperationType opType, User user) {
        this.tissueRepo = tissueRepo;
        this.sampleRepo = sampleRepo;
        this.slotRepo = slotRepo;
        this.lwRepo = lwRepo;
        this.opcomRepo = opcomRepo;
        this.lwService = lwService;
        this.opService = opService;
        this.workService = workService;
        this.bioRiskService = bioRiskService;
        this.request = request;
        this.lwData = lwData;
        this.medium = medium;
        this.bioState = bioState;
        this.work = work;
        this.opType = opType;
        this.user = user;
    }

    @Override
    public OperationResult record() {
        List<Labware> labware = createLabware();
        createSamples();
        findSlots();
        fillLabware();
        List<Operation> ops = createOperations();
        if (work != null) {
            workService.link(work, ops);
        }
        bioRiskService.copyOpSampleBioRisks(ops);
        discardSources();
        return new OperationResult(ops, labware);
    }

    /** Create the destination labware, initially empty */
    public List<Labware> createLabware() {
        List<Labware> labware = new ArrayList<>(lwData.size());
        for (BlockLabwareData lwd : lwData) {
            String prebarcode = lwd.getRequestLabware().getPreBarcode();
            // Uses the prebarcode for the labware external barcode and the de facto barcode
            Labware lw = lwService.create(lwd.getLwType(), prebarcode, prebarcode);
            labware.add(lw);
            lwd.setLabware(lw);
        }
        return labware;
    }

    /** Either return the tissue or create a new tissue matching the given fields */
    public Tissue getOrCreateTissue(Tissue original, String replicate, Medium medium) {
        boolean changeReplicate = (!nullOrEmpty(replicate) && !replicate.equalsIgnoreCase(original.getReplicate()));
        boolean changeMedium = (medium!=null && !medium.getId().equals(original.getMedium().getId()));
        if (!changeReplicate && !changeMedium) {
            return original;
        }
        Tissue tissue = original.derived();
        if (changeReplicate) {
            tissue.setReplicate(replicate.toLowerCase());
        }
        if (changeMedium) {
            tissue.setMedium(medium);
        }
        return tissueRepo.save(tissue);
    }

    /** Creates the samples for the requested blocks */
    public void createSamples() {
        for (BlockData bd : iter(blockDataStream())) {
            Tissue original = bd.getSourceSample().getTissue();
            Tissue tissue = getOrCreateTissue(original, bd.getRequestContent().getReplicate(), medium);
            Sample sample = sampleRepo.save(Sample.newBlock(null, tissue, bioState, 0));
            bd.setSample(sample);
        }
    }

    /**
     * Finds the source and destination slots for each block
     */
    public void findSlots() {
        for (BlockLabwareData lwd : lwData) {
            Labware destLabware = lwd.getLabware();
            for (BlockData bd : lwd.getBlocks()) {
                Set<Address> destAddresses = new HashSet<>(bd.getRequestContent().getAddresses());
                List<Slot> destSlots = destLabware.getSlots().stream()
                        .filter(slot -> destAddresses.contains(slot.getAddress()))
                        .toList();
                bd.setDestSlots(destSlots);
                Integer sampleId = bd.getSourceSample().getId();
                List<Slot> sourceSlots = bd.getSourceLabware().getSlots().stream()
                        .filter(slot -> slot.getSamples().stream().anyMatch(sam -> sam.getId().equals(sampleId)))
                        .toList();
                bd.setSourceSlots(sourceSlots);
            }
        }
    }

    /** Creates the required operations and records the requested comments */
    public List<Operation> createOperations() {
        List<Operation> ops = new ArrayList<>(lwData.size());
        for (BlockLabwareData lwd : lwData) {
            Operation op = createOperation(lwd);
            recordComments(lwd, op.getId());
            ops.add(op);
        }
        return ops;
    }

    /**
     * Creates an operation for the specified destination labware
     * with an action for each combination of source/destination slot
     */
    public Operation createOperation(BlockLabwareData lwd) {
        List<Action> actions = new ArrayList<>();
        for (BlockData bd : lwd.getBlocks()) {
            Sample sample = bd.getSample();
            Sample sourceSample = bd.getSourceSample();
            for (Slot destSlot : bd.getDestSlots()) {
                for (Slot sourceSlot : bd.getSourceSlots()) {
                    actions.add(new Action(null, null, sourceSlot, destSlot, sample, sourceSample));
                }
            }
        }
        return opService.createOperation(opType, user, actions, null);
    }

    /** Records the requested comments against the specified operation */
    public void recordComments(BlockLabwareData lwd, Integer opId) {
        List<OperationComment> opcoms = new ArrayList<>();
        for (BlockData bd : lwd.getBlocks()) {
            Comment com = bd.getComment();
            if (com == null) {
                continue;
            }
            Integer sampleId = bd.getSample().getId();
            for (Slot destSlot : bd.getDestSlots()) {
                OperationComment opcom = new OperationComment(null, com, opId, sampleId, destSlot.getId(), null);
                opcoms.add(opcom);
            }
        }
        if (!opcoms.isEmpty()) {
            opcomRepo.saveAll(opcoms);
        }
    }

    /** Puts the new samples into the slots and saves the updated slots */
    public void fillLabware() {
        Set<Slot> slotsToUpdate = new HashSet<>();
        for (BlockData bd : iter(blockDataStream())) {
            for (Slot slot : bd.getDestSlots()) {
                slot.addSample(bd.getSample());
                slotsToUpdate.add(slot);
            }
        }
        slotRepo.saveAll(slotsToUpdate);
    }

    /** Marks any sources discarded if the request specifies that we do so */
    public void discardSources() {
        if (!nullOrEmpty(request.getDiscardSourceBarcodes())) {
            Set<String> discardBarcodes = request.getDiscardSourceBarcodes().stream()
                    .map(String::toUpperCase)
                    .collect(toSet());
            Set<Labware> lwToDiscard = blockDataStream().map(BlockData::getSourceLabware)
                    .filter(lw -> !lw.isDiscarded() && discardBarcodes.contains(lw.getBarcode().toUpperCase()))
                    .collect(toSet());
            lwToDiscard.forEach(lw -> lw.setDiscarded(true));
            lwRepo.saveAll(lwToDiscard);
        }
    }

    /** Stream of block data from the request */
    Stream<BlockData> blockDataStream() {
        return lwData.stream()
                .flatMap(lw -> lw.getBlocks().stream());
    }
}
