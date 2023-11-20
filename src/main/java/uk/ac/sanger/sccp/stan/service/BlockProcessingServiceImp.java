package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.TissueBlockRequest;
import uk.ac.sanger.sccp.stan.request.TissueBlockRequest.TissueBlockLabware;
import uk.ac.sanger.sccp.stan.service.store.StoreService;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * @author dr6
 */
@Service
public class BlockProcessingServiceImp implements BlockProcessingService {
    private final LabwareValidatorFactory lwValFactory;
    private final Validator<String> prebarcodeValidator;
    private final Validator<String> replicateValidator;
    private final LabwareRepo lwRepo;
    private final SlotRepo slotRepo;
    private final OperationTypeRepo opTypeRepo;
    private final OperationCommentRepo opCommentRepo;
    private final LabwareTypeRepo ltRepo;
    private final BioStateRepo bsRepo;
    private final TissueRepo tissueRepo;
    private final SampleRepo sampleRepo;
    private final CommentValidationService commentValidationService;
    private final OperationService opService;
    private final LabwareService lwService;
    private final WorkService workService;
    private final StoreService storeService;

    private final Transactor transactor;

    @Autowired
    public BlockProcessingServiceImp(LabwareValidatorFactory lwValFactory,
                                     @Qualifier("tubePrebarcodeValidator") Validator<String> prebarcodeValidator,
                                     @Qualifier("replicateValidator") Validator<String> replicateValidator,
                                     LabwareRepo lwRepo, SlotRepo slotRepo, OperationTypeRepo opTypeRepo,
                                     OperationCommentRepo opCommentRepo, LabwareTypeRepo ltRepo,
                                     BioStateRepo bsRepo, TissueRepo tissueRepo, SampleRepo sampleRepo,
                                     CommentValidationService commentValidationService, OperationService opService,
                                     LabwareService lwService, WorkService workService, StoreService storeService, Transactor transactor) {
        this.lwValFactory = lwValFactory;
        this.prebarcodeValidator = prebarcodeValidator;
        this.replicateValidator = replicateValidator;
        this.lwRepo = lwRepo;
        this.slotRepo = slotRepo;
        this.opTypeRepo = opTypeRepo;
        this.opCommentRepo = opCommentRepo;
        this.ltRepo = ltRepo;
        this.bsRepo = bsRepo;
        this.tissueRepo = tissueRepo;
        this.sampleRepo = sampleRepo;
        this.commentValidationService = commentValidationService;
        this.opService = opService;
        this.lwService = lwService;
        this.workService = workService;
        this.storeService = storeService;
        this.transactor = transactor;
    }

    @Override
    public OperationResult perform(User user, TissueBlockRequest request) throws ValidationException {
        OperationResult opres = transactor.transact("Block processing", () -> performInsideTransaction(user, request));
        if (!request.getDiscardSourceBarcodes().isEmpty()) {
            storeService.discardStorage(user, request.getDiscardSourceBarcodes());
        }
        return opres;
    }

    public OperationResult performInsideTransaction(User user, TissueBlockRequest request) throws ValidationException {
        requireNonNull(user, "User is null.");
        requireNonNull(request, "Request is null.");
        Collection<String> problems = new LinkedHashSet<>();
        if (request.getLabware().isEmpty()) {
            problems.add("No labware specified in request.");
            throw new ValidationException("The request could not be validated.", problems);
        }
        UCMap<Labware> sources = loadSources(problems, request);
        Work work = workService.validateUsableWork(problems, request.getWorkNumber());
        UCMap<LabwareType> lwTypes = loadEntities(problems, request, TissueBlockLabware::getLabwareType,
                "Labware type", LabwareType::getName, true, ltRepo::findAllByNameIn);
        checkPrebarcodes(problems, request, lwTypes);
        Map<Integer, Comment> comments = loadComments(problems, request);
        checkReplicates(problems, request, sources);
        checkDiscardBarcodes(problems, request);
        if (!problems.isEmpty()) {
            throw new ValidationException("The request could not be validated.", problems);
        }

        List<Sample> samples = createSamples(request, sources);
        List<Labware> destinations = createDestinations(request, samples, lwTypes);
        List<Operation> ops = createOperations(request, user, sources, destinations, comments);
        if (work!=null) {
            workService.link(work, ops);
        }
        discardSources(request.getDiscardSourceBarcodes(), sources);

        return new OperationResult(ops, destinations);
    }

    /**
     * Loads the source labware for the request, and checks they are suitable
     * @param problems receptacle for problems found
     * @param request the request
     * @return the loaded labware, mapped from barcodes
     */
    public UCMap<Labware> loadSources(Collection<String> problems, TissueBlockRequest request) {
        BioState bs = bsRepo.getByName("Original sample");
        Set<String> barcodes = new HashSet<>();
        boolean anyMissing = false;
        for (var block : request.getLabware()) {
            String barcode = block.getSourceBarcode();
            if (nullOrEmpty(barcode)) {
                anyMissing = true;
            } else {
                barcodes.add(barcode.toUpperCase());
            }
        }
        LabwareValidator val = lwValFactory.getValidator();
        if (anyMissing) {
            problems.add("Source barcode missing.");
        }
        val.loadLabware(lwRepo, barcodes);
        val.setSingleSample(true);
        val.validateSources();
        val.validateBioState(bs);
        problems.addAll(val.getErrors());

        return UCMap.from(val.getLabware(), Labware::getBarcode);
    }

    /**
     * Checks that the prebarcodes look appropriate.
     * Prebarcodes should be given for labware types that require it; and not given for labware types
     * that do not require it.
     * Prebarcodes should be of the expected format.
     * Prebarcodes should be unique.
     * @param problems receptacle for problems found
     * @param request the request
     * @param lwTypes the map to look up labware types
     */
    public void checkPrebarcodes(Collection<String> problems, TissueBlockRequest request, UCMap<LabwareType> lwTypes) {
        Set<String> missingBarcodeForLwTypes = new LinkedHashSet<>();
        Set<String> unexpectedBarcodeForLwTypes = new LinkedHashSet<>();
        Set<String> prebarcodes = new HashSet<>();
        Set<String> dupes = new LinkedHashSet<>();
        for (TissueBlockLabware block : request.getLabware()) {
            LabwareType lt = lwTypes.get(block.getLabwareType());
            String barcode = block.getPreBarcode();
            if (nullOrEmpty(barcode)) {
                if (lt!=null && lt.isPrebarcoded()) {
                    missingBarcodeForLwTypes.add(lt.getName());
                }
            } else {
                prebarcodeValidator.validate(barcode, problems::add);
                if (lt!=null && !lt.isPrebarcoded()) {
                    unexpectedBarcodeForLwTypes.add(lt.getName());
                }
                barcode = barcode.toUpperCase();
                if (!prebarcodes.add(barcode)) {
                    dupes.add(barcode);
                }
            }
        }
        if (!missingBarcodeForLwTypes.isEmpty()) {
            problems.add(pluralise("A barcode is required for labware type{s} ", missingBarcodeForLwTypes.size()) +
                    missingBarcodeForLwTypes + ".");
        }
        if (!unexpectedBarcodeForLwTypes.isEmpty()) {
            problems.add(pluralise("A barcode is not expected for labware type{s} ", unexpectedBarcodeForLwTypes.size()) +
                    unexpectedBarcodeForLwTypes + ".");
        }
        if (!dupes.isEmpty()) {
            problems.add("Barcode specified multiple times: "+dupes);
        }
        if (!prebarcodes.isEmpty()) {
            Collection<Labware> existing = lwRepo.findByBarcodeIn(prebarcodes);
            if (!existing.isEmpty()) {
                List<String> existingBarcodes = existing.stream()
                        .map(Labware::getBarcode)
                        .collect(toList());
                problems.add("Barcode already in use: "+existingBarcodes);
            } else {
                existing = lwRepo.findByExternalBarcodeIn(prebarcodes);
                if (!existing.isEmpty()) {
                    List<String> existingBarcodes = existing.stream()
                            .map(Labware::getExternalBarcode)
                            .collect(toList());
                    problems.add("External barcode already in use: " + existingBarcodes);
                }
            }
        }
    }

    /**
     * Validates and loads comments. Comments are optional, so null comment ids are ignored.
     * @param problems receptacle for problems
     * @param request the request
     * @return a map of comments from their ids
     */
    public Map<Integer, Comment> loadComments(Collection<String> problems, TissueBlockRequest request) {
        Collection<Comment> comments = commentValidationService.validateCommentIds(problems,
                request.getLabware().stream()
                        .map(TissueBlockLabware::getCommentId)
                        .filter(Objects::nonNull)
        );
        if (comments.isEmpty()) {
            return Map.of();
        }
        return comments.stream().collect(BasicUtils.inMap(Comment::getId));
    }

    /**
     * Loads entities specified by a string field.
     * @param problems receptacle for problems
     * @param request the request
     * @param fieldGetter the function to get the string from the block in the request
     * @param fieldName the name of the field (for problem messages)
     * @param entityFieldGetter the function to get the string from the entity
     * @param required whether it is a problem for a string to be missing (null or empty)
     * @param lookup function to look up matching entities (collectively) from a collection
     * @return a map of the entities found from the strings values for the specified field
     * @param <T> the type of entity
     */
    public <T> UCMap<T> loadEntities(Collection<String> problems, TissueBlockRequest request,
                                     Function<TissueBlockLabware, String> fieldGetter, String fieldName,
                                     Function<T, String> entityFieldGetter,
                                     boolean required, Function<Collection<String>, ? extends Collection<T>> lookup) {
        boolean anyMissing = false;
        Set<String> fieldValues = new LinkedHashSet<>();
        for (var block : request.getLabware()) {
            String value = fieldGetter.apply(block);
            if (nullOrEmpty(value)) {
                anyMissing = true;
            } else {
                fieldValues.add(value);
            }
        }
        if (anyMissing && required) {
            problems.add(fieldName+" missing.");
        }
        if (fieldValues.isEmpty()) {
            return new UCMap<>();
        }
        UCMap<T> entities = UCMap.from(lookup.apply(fieldValues), entityFieldGetter);
        List<String> unknown = fieldValues.stream()
                .filter(value -> entities.get(value)==null)
                .filter(distinctUCSerial())
                .map(BasicUtils::repr)
                .collect(toList());
        if (!unknown.isEmpty()) {
            problems.add(fieldName+" unknown: "+unknown);
        }
        return entities;
    }

    /**
     * Checks that the replicate field is given, correctly formatted, and unique
     * @param problems receptacle for problems
     * @param request the request
     * @param sources map to look up source labware from its barcode
     */
    public void checkReplicates(Collection<String> problems, TissueBlockRequest request, UCMap<Labware> sources) {
        boolean anyMissing = false;
        Set<RepKey> repKeys = new LinkedHashSet<>();
        Set<RepKey> repKeyDupes = new LinkedHashSet<>();
        for (var block : request.getLabware()) {
            if (nullOrEmpty(block.getReplicate())) {
                anyMissing = true;
            } else if (replicateValidator.validate(block.getReplicate(), problems::add)) {
                RepKey repKey = RepKey.from(sources.get(block.getSourceBarcode()), block.getReplicate());
                if (repKey!=null && !repKeys.add(repKey)) {
                    repKeyDupes.add(repKey);
                }
            }
        }
        if (!repKeyDupes.isEmpty()) {
            problems.add("Same replicate specified multiple times: "+repKeyDupes);
        }
        if (anyMissing) {
            problems.add("Missing replicate for some blocks.");
        }
        List<RepKey> alreadyExistRepKeys = repKeys.stream()
                .filter(rp -> !tissueRepo.findByDonorIdAndSpatialLocationIdAndReplicate(
                        rp.donorId(), rp.spatialLocationId(), rp.replicate()).isEmpty()
                ).collect(toList());
        if (!alreadyExistRepKeys.isEmpty()) {
            problems.add("Replicate already exists in the database: "+alreadyExistRepKeys);
        }
    }

    /**
     * Checks that the barcodes specified to discard are all source barcodes in the request.
     * @param problems receptacle for problems
     * @param request the request
     */
    public void checkDiscardBarcodes(Collection<String> problems, TissueBlockRequest request) {
        if (request.getDiscardSourceBarcodes().isEmpty()) {
            return;
        }
        Set<String> sourceBarcodes = request.getLabware().stream()
                .map(TissueBlockLabware::getSourceBarcode)
                .filter(s -> !nullOrEmpty(s))
                .map(String::toUpperCase)
                .collect(toSet());
        boolean anyNull = false;
        Set<String> missingBarcodes = new LinkedHashSet<>();
        for (String barcode : request.getDiscardSourceBarcodes()) {
            if (nullOrEmpty(barcode)) {
                anyNull = true;
            } else if (!sourceBarcodes.contains(barcode.toUpperCase())) {
                missingBarcodes.add(repr(barcode));
            }
        }
        if (anyNull) {
            problems.add("A null or empty string was supplied as a barcode to discard.");
        }
        if (!missingBarcodes.isEmpty()) {
            problems.add(pluralise("The given list of barcodes to discard includes {a |}barcode{s} " +
                    "that {is|are} not specified as {a |}source barcode{s} in this request: ", missingBarcodes.size())
                    + missingBarcodes);
        }
    }

    /**
     * Creates new samples, in a list parallel to the blocks specified in the request.
     * @param request the request
     * @param sources the source labware
     * @return a list of samples, corresponding to the respective blocks in the request
     */
    public List<Sample> createSamples(TissueBlockRequest request, UCMap<Labware> sources) {
        final BioState bs = bsRepo.getByName("Tissue");
        return request.getLabware()
                .stream()
                .map(block -> createSample(block, sources.get(block.getSourceBarcode()), bs))
                .collect(toList());
    }

    /**
     * Creates a new tissue block and sample.
     * @param block the specification of the block
     * @param sourceLabware the source labware for the new block
     * @param bs the bio state for the new block
     * @return the newly created sample
     */
    public Sample createSample(TissueBlockLabware block, Labware sourceLabware, BioState bs) {
        Tissue original = getSample(sourceLabware)
                .map(Sample::getTissue)
                .orElseThrow();
        Tissue newTissue = new Tissue(null, original.getExternalName(), block.getReplicate().toLowerCase(),
                original.getSpatialLocation(), original.getDonor(), original.getMedium(),
                original.getFixative(), original.getHmdmc(), original.getCollectionDate(),
                original.getId());
        Tissue tissue = tissueRepo.save(newTissue);
        Sample newSample = new Sample(null, null, tissue, bs);
        return sampleRepo.save(newSample);
    }

    /**
     * Creates destinations for the given request.
     * @param request the request
     * @param samples the samples for the respective blocks of the request
     * @param lwTypes map to look up labware types by name
     * @return a list of labware for respective blocks of the request
     */
    public List<Labware> createDestinations(TissueBlockRequest request, List<Sample> samples, UCMap<LabwareType> lwTypes) {
        final var sampleIter = samples.iterator();
        return request.getLabware().stream()
                .map(block -> createDestination(lwTypes.get(block.getLabwareType()), sampleIter.next(), block.getPreBarcode()))
                .collect(toList());
    }

    /**
     * Creates labware containing a sample.
     * @param lwType the type of labware
     * @param sample the sample the labware will contain in its first slot
     * @param preBarcode the prebarcode (if any), or null
     * @return the newly created labware, containing the sample
     */
    public Labware createDestination(LabwareType lwType, Sample sample, String preBarcode) {
        Labware lw = lwService.create(lwType, preBarcode, preBarcode);
        Slot slot = lw.getFirstSlot();
        slot.addSample(sample);
        slot.setBlockSampleId(sample.getId());
        slot.setBlockHighestSection(0);
        slotRepo.save(slot);
        return lw;
    }

    /**
     * Creates block processing operations.
     * @param request the block processing request
     * @param user the user responsible for the operations
     * @param sources map to look up source labware by its barcode
     * @param destinations the labware for the respective blocks of the request
     * @param comments map to look up comments by their id
     * @return the operations for the respective blocks in the request
     */
    public List<Operation> createOperations(TissueBlockRequest request, User user,
                                            UCMap<Labware> sources, List<Labware> destinations,
                                            Map<Integer, Comment> comments) {
        final OperationType opType = opTypeRepo.getByName("Block processing");
        final var destIter = destinations.iterator();
        return request.getLabware().stream()
                .map(block -> createOperation(opType, user, sources.get(block.getSourceBarcode()), destIter.next(),
                        block.getCommentId()==null ? null : comments.get(block.getCommentId())))
                .collect(toList());
    }

    /**
     * Creates an operation as specified
     * @param opType the type of op
     * @param user the user responsible for the op
     * @param sourceLw the source labware (containing one sample in one slot)
     * @param destLw the destination labware (containing one sample in its first slot)
     * @param comment the comment to record (if any), or null
     * @return the created operation
     */
    public Operation createOperation(OperationType opType, User user,
                                     Labware sourceLw, Labware destLw, Comment comment) {
        Slot src = sourceLw.getSlots().stream()
                .filter(slot -> !slot.getSamples().isEmpty())
                .findAny()
                .orElseThrow();
        Slot dest = destLw.getFirstSlot();
        Sample srcSample = src.getSamples().get(0);
        Sample dstSample = dest.getSamples().get(0);
        Action action = new Action(null, null, src, dest, dstSample, srcSample);
        Operation op = opService.createOperation(opType, user, List.of(action), null);
        if (comment!=null) {
            opCommentRepo.save(new OperationComment(null, comment, op.getId(), dstSample.getId(), dest.getId(), null));
        }
        return op;
    }

    /**
     * Discards the indicated labware
     * @param barcodes barcodes of labware to discard
     * @param lwMap map to look up labware by its barcode
     */
    public void discardSources(Collection<String> barcodes, UCMap<Labware> lwMap) {
        if (!barcodes.isEmpty()) {
            List<Labware> labwareToSave = new ArrayList<>(barcodes.size());
            for (String bc : barcodes) {
                Labware lw = lwMap.get(bc);
                if (!lw.isDiscarded()) {
                    lw.setDiscarded(true);
                    labwareToSave.add(lw);
                }
            }
            lwRepo.saveAll(labwareToSave);
        }
    }

    /** The unique fields associated with a block */
    record RepKey(Donor donor, SpatialLocation spatialLocation, String replicate) {
        RepKey(Donor donor, SpatialLocation spatialLocation, String replicate) {
            this.donor = donor;
            this.spatialLocation = spatialLocation;
            this.replicate = replicate.toLowerCase();
        }

        Integer donorId() {
            return this.donor.getId();
        }
        Integer spatialLocationId() {
            return this.spatialLocation.getId();
        }

        static RepKey from(Labware sourceLabware, String replicate) {
            if (sourceLabware==null) {
                return null;
            }
            Tissue tissue = getSample(sourceLabware)
                    .map(Sample::getTissue)
                    .orElse(null);
            if (tissue==null) {
                return null;
            }
            return new RepKey(tissue.getDonor(), tissue.getSpatialLocation(), replicate);
        }


        @Override
        public String toString() {
            return String.format("{Donor: %s, Tissue type: %s, Spatial location: %s, Replicate: %s}",
                    donor.getDonorName(), spatialLocation.getTissueType().getName(), spatialLocation.getCode(),
                    replicate);
        }
    }

    /**
     * Helper function to get the sample from a piece of labware.
     * @param lw the piece of labware
     * @return an optional sample contained in the labware
     */
    public static Optional<Sample> getSample(Labware lw) {
        return lw.getSlots().stream()
                .flatMap(slot -> slot.getSamples().stream())
                .findAny();
    }
}
