package uk.ac.sanger.sccp.stan.service.block;

import org.jetbrains.annotations.NotNull;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.TissueBlockRequest;
import uk.ac.sanger.sccp.stan.request.TissueBlockRequest.TissueBlockContent;
import uk.ac.sanger.sccp.stan.request.TissueBlockRequest.TissueBlockLabware;
import uk.ac.sanger.sccp.stan.service.*;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * Helper to validate a block request.
 * @author dr6
 */
public class BlockValidatorImp implements BlockValidator {
    private final LabwareValidatorFactory lwValFactory;
    private final Validator<String> prebarcodeValidator;
    private final Validator<String> replicateValidator;
    private final LabwareRepo lwRepo;
    private final OperationTypeRepo opTypeRepo;
    private final LabwareTypeRepo ltRepo;
    private final BioStateRepo bsRepo;
    private final TissueRepo tissueRepo;
    private final MediumRepo mediumRepo;
    private final CommentValidationService commentValidationService;
    private final WorkService workService;

    private final TissueBlockRequest request;
    private List<BlockLabwareData> lwData;
    private Work work;
    private BioState bioState;
    private Medium medium;
    private OperationType opType;

    private Collection<String> problems;

    public BlockValidatorImp(LabwareValidatorFactory lwValFactory,
                             Validator<String> prebarcodeValidator, Validator<String> replicateValidator,
                             LabwareRepo lwRepo, OperationTypeRepo opTypeRepo, LabwareTypeRepo ltRepo,
                             BioStateRepo bsRepo, TissueRepo tissueRepo, MediumRepo mediumRepo,
                             CommentValidationService commentValidationService, WorkService workService,
                             TissueBlockRequest request) {
        this.lwValFactory = lwValFactory;
        this.prebarcodeValidator = prebarcodeValidator;
        this.replicateValidator = replicateValidator;
        this.lwRepo = lwRepo;
        this.opTypeRepo = opTypeRepo;
        this.ltRepo = ltRepo;
        this.bsRepo = bsRepo;
        this.tissueRepo = tissueRepo;
        this.mediumRepo = mediumRepo;
        this.commentValidationService = commentValidationService;
        this.workService = workService;
        this.request = request;
    }

    /** Loads an entity from the database or returns null and records a problem if it is not found. */
    <E> E loadFromOpt(String typeName, Function<String, Optional<E>> loader, String name) {
        Optional<E> opt = loader.apply(name);
        if (opt.isPresent()) {
            return opt.get();
        }
        problems.add(String.format("%s %s not found in database.", typeName, repr(name)));
        return null;
    }

    @Override
    public void validate() {
        setProblems(new LinkedHashSet<>());
        if (nullOrEmpty(request.getLabware())) {
            problems.add("No labware specified.");
            return;
        }
        setLwData(request.getLabware().stream().map(BlockLabwareData::new).toList());
        loadEntities();
        checkDestAddresses();
        checkPrebarcodes();
        checkReplicates();
        checkDiscardBarcodes();
    }

    /**
     * Loads the entities referenced by the request.
     */
    public void loadEntities() {
        setWork(workService.validateUsableWork(problems, request.getWorkNumber()));
        setBioState(loadFromOpt("Bio state", bsRepo::findByName, "Original sample"));
        setMedium(loadFromOpt("Medium", mediumRepo::findByName, "OCT"));
        setOpType(loadFromOpt("Operation type", opTypeRepo::findByName, "Block processing"));
        loadSources();
        loadSourceSamples();
        loadLabwareTypes();
        loadComments();
    }

    /**
     * Loads the source labware into a map.
     */
    public void loadSources() {
        Set<String> barcodes = new HashSet<>();
        boolean anyMissing = false;
        for (var block : iter(requestContents())) {
            String barcode = block.getSourceBarcode();
            if (nullOrEmpty(barcode)) {
                anyMissing = true;
            } else {
                barcodes.add(barcode.toUpperCase());
            }
        }
        if (anyMissing) {
            problems.add("Source barcode missing.");
        }
        LabwareValidator val = lwValFactory.getValidator();
        val.loadLabware(lwRepo, barcodes);
        val.validateSources();
        if (bioState!=null) {
            val.validateBioState(bioState);
        }
        problems.addAll(val.getErrors());
        UCMap<Labware> sourceLabware = UCMap.from(val.getLabware(), Labware::getBarcode);
        for (BlockData bd : iter(blockDatas())) {
            bd.setSourceLabware(sourceLabware.get(bd.getRequestContent().getSourceBarcode()));
        }
    }

    /** Loads the source samples into a map. */
    public void loadSourceSamples() {
        boolean anyMissing = false;
        for (var lwd : lwData) {
            for (var bd : lwd.getBlocks()) {
                Integer sourceSampleId = bd.getRequestContent().getSourceSampleId();
                if (sourceSampleId==null) {
                    anyMissing = true;
                    continue;
                }

                Labware lw = bd.getSourceLabware();
                if (lw==null) {
                    continue;
                }
                Sample sample = lw.getSlots().stream().flatMap(slot -> slot.getSamples().stream())
                        .filter(sam -> sam.getId().equals(sourceSampleId))
                        .findFirst()
                        .orElse(null);
                if (sample==null) {
                    problems.add(String.format("Sample id %s not present in labware %s.", sourceSampleId, lw.getBarcode()));
                } else {
                    bd.setSourceSample(sample);
                }
            }
        }
        if (anyMissing) {
            problems.add("Source sample ID missing from block request.");
        }
    }

    /** Loads the labware types into the data objects. */
    public void loadLabwareTypes() {
        Set<String> lwTypeNames = lwData.stream()
                .map(ld -> ld.getRequestLabware().getLabwareType())
                .filter(s -> !nullOrEmpty(s))
                .map(String::toUpperCase)
                .collect(toSet());
        UCMap<LabwareType> lwTypes = UCMap.from(ltRepo.findAllByNameIn(lwTypeNames), LabwareType::getName);
        boolean anyMissing = false;
        Set<String> unknown = new LinkedHashSet<>();
        for (BlockLabwareData ld : lwData) {
            TissueBlockLabware rlw = ld.getRequestLabware();
            if (nullOrEmpty(rlw.getLabwareType())) {
                anyMissing = true;
            } else {
                LabwareType lt = lwTypes.get(rlw.getLabwareType());
                if (lt==null) {
                    unknown.add(repr(rlw.getLabwareType()));
                } else {
                    ld.setLwType(lt);
                }
            }
        }
        if (anyMissing) {
            problems.add("Missing labware type.");
        }
        if (!unknown.isEmpty()) {
            problems.add("Unknown labware types: "+unknown);
        }
    }

    /** Loads the referenced comment into the data objects. */
    public void loadComments() {
        Stream<Integer> commentIdStream = requestContents()
                .map(TissueBlockContent::getCommentId)
                .filter(Objects::nonNull);
        Map<Integer, Comment> commentMap = commentValidationService.validateCommentIds(problems, commentIdStream).stream()
                .collect(inMap(Comment::getId));
        for (BlockData bd : iter(lwData.stream().flatMap(ld -> ld.getBlocks().stream()))) {
            bd.setComment(commentMap.get(bd.getRequestContent().getCommentId()));
        }
    }

    /** Checks that the destination addresses are valid. */
    public void checkDestAddresses() {
        boolean anyMissing = false;
        for (var lwd : lwData) {
            LabwareType lt = lwd.getLwType();
            for (var bl : lwd.getBlocks()) {
                List<Address> addresses = bl.getRequestContent().getAddresses();
                if (nullOrEmpty(addresses)) {
                    anyMissing = true;
                } else {
                    for (Address addr : addresses) {
                        if (lt != null && lt.indexOf(addr) < 0) {
                            problems.add(String.format("Slot address %s not valid in labware type %s.", addr, lt.getName()));
                        }
                    }
                }
            }
        }
        if (anyMissing) {
            problems.add("Destination slot addresses missing from block request.");
        }
    }

    /**
     * Checks that the prebarcodes look appropriate.
     * Prebarcodes should be given for labware types that require it; and not given for labware types
     * that do not require it.
     * Prebarcodes should be of the expected format.
     * Prebarcodes should be unique.
     */
    public void checkPrebarcodes() {
        Set<String> missingBarcodeForLwTypes = new LinkedHashSet<>();
        Set<String> unexpectedBarcodeForLwTypes = new LinkedHashSet<>();
        Set<String> prebarcodes = new HashSet<>();
        Set<String> dupes = new LinkedHashSet<>();
        for (var lwd : lwData) {
            LabwareType lt = lwd.getLwType();
            String barcode = lwd.getRequestLabware().getPreBarcode();
            if (nullOrEmpty(barcode)) {
                if (lt != null && lt.isPrebarcoded()) {
                    missingBarcodeForLwTypes.add(lt.getName());
                }
            } else {
                prebarcodeValidator.validate(barcode, problems::add);
                if (lt != null && !lt.isPrebarcoded()) {
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
                        .toList();
                problems.add("Barcode already in use: "+existingBarcodes);
            } else {
                existing = lwRepo.findByExternalBarcodeIn(prebarcodes);
                if (!existing.isEmpty()) {
                    List<String> existingBarcodes = existing.stream()
                            .map(Labware::getExternalBarcode)
                            .toList();
                    problems.add("External barcode already in use: " + existingBarcodes);
                }
            }
        }

    }

    /**
     * Checks the formatting of the replicate field, if provided; otherwise, assigns the same replicate as the source.
     */
    public void checkReplicates() {
        boolean anyMissing = false;
        boolean differentRep = false;
        Set<RepKey> repKeys = new LinkedHashSet<>();
        Set<RepKey> repKeyDupes = new LinkedHashSet<>();
        for (var bl : iter(blockDatas())) {
            String newRep = bl.getRequestContent().getReplicate();
            Sample sourceSample = bl.getSourceSample();
            String sourceRep = Optional.ofNullable(sourceSample)
                    .map(Sample::getTissue)
                    .map(Tissue::getReplicate)
                    .orElse(null);
            if (nullOrEmpty(sourceRep)) {
                if (nullOrEmpty(newRep)) {
                    anyMissing = true;
                } else if (replicateValidator.validate(newRep, problems::add)) {
                    RepKey repKey = RepKey.from(sourceSample, bl.getRequestContent().getReplicate());
                    if (repKey != null && !repKeys.add(repKey)) {
                        repKeyDupes.add(repKey);
                    }
                }
            } else if (nullOrEmpty(newRep)) {
                bl.getRequestContent().setReplicate(sourceRep);
            } else if (!newRep.equalsIgnoreCase(sourceRep)) {
                differentRep = true;
            }
        }

        if (!repKeyDupes.isEmpty()) {
            problems.add("Same replicate specified multiple times: " + repKeyDupes);
        }
        if (anyMissing) {
            problems.add("Missing replicate for some blocks.");
        }
        if (differentRep) {
            problems.add("Replicate numbers must match the source replicate number where present.");
        }
        List<RepKey> alreadyExistRepKeys = repKeys.stream()
                .filter(rp -> !tissueRepo.findByDonorIdAndSpatialLocationIdAndReplicate(
                        rp.donorId(), rp.spatialLocationId(), rp.replicate()).isEmpty()
                ).toList();
        if (!alreadyExistRepKeys.isEmpty()) {
            problems.add("Replicate already exists in the database: " + alreadyExistRepKeys);
        }
    }

    /** Checks that the discard barcodes are source barcodes in the request. */
    public void checkDiscardBarcodes() {
        List<String> discardBarcodes = request.getDiscardSourceBarcodes();
        if (discardBarcodes.isEmpty()) {
            return;
        }
        Set<String> sourceBarcodes = requestContents()
                .map(TissueBlockContent::getSourceBarcode)
                .filter(s -> !nullOrEmpty(s))
                .map(String::toUpperCase)
                .collect(toSet());
        boolean anyNull = false;
        Set<String> missingBarcodes = new LinkedHashSet<>();
        for (String barcode : discardBarcodes) {
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

    public void setLwData(List<BlockLabwareData> lwData) {
        this.lwData = lwData;
    }

    @Override
    public List<BlockLabwareData> getLwData() {
        return this.lwData;
    }

    public void setWork(Work work) {
        this.work = work;
    }

    @Override
    public Work getWork() {
        return this.work;
    }

    public void setBioState(BioState bioState) {
        this.bioState = bioState;
    }

    @Override
    public BioState getBioState() {
        return this.bioState;
    }

    public void setMedium(Medium medium) {
        this.medium = medium;
    }

    @Override
    public Medium getMedium() {
        return this.medium;
    }

    public void setOpType(OperationType opType) {
        this.opType = opType;
    }

    @Override
    public OperationType getOpType() {
        return this.opType;
    }

    public void setProblems(Collection<String> problems) {
        this.problems = problems;
    }

    @Override
    public Collection<String> getProblems() {
        return this.problems;
    }

    @Override
    public void raiseError() {
        if (!problems.isEmpty()) {
            throw new ValidationException(problems);
        }
    }

    /** A stream of the contents in the request. */
    Stream<TissueBlockContent> requestContents() {
        return request.getLabware().stream().flatMap(rlw -> rlw.getContents().stream());
    }

    /** A stream of the block data for the request */
    Stream<BlockData> blockDatas() {
        return lwData.stream().flatMap(lwd -> lwd.getBlocks().stream());
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

        static RepKey from(Sample sample, String replicate) {
            if (sample==null) {
                return null;
            }
            Tissue tissue = sample.getTissue();
            if (tissue==null) {
                return null;
            }
            return new RepKey(tissue.getDonor(), tissue.getSpatialLocation(), replicate);
        }

        @NotNull
        @Override
        public String toString() {
            return String.format("{Donor: %s, Tissue type: %s, Spatial location: %s, Replicate: %s}",
                    donor.getDonorName(), spatialLocation.getTissueType().getName(), spatialLocation.getCode(),
                    replicate);
        }
    }
}
