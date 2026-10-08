package uk.ac.sanger.sccp.stan.service;

import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.Transactor;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OperationResult;
import uk.ac.sanger.sccp.stan.request.PotProcessingRequest;
import uk.ac.sanger.sccp.stan.request.PotProcessingRequest.PotProcessingDestination;
import uk.ac.sanger.sccp.stan.service.store.StoreService;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.*;

import java.util.*;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * @author dr6
 */
@Service
public class PotProcessingServiceImp implements PotProcessingService {
    private final LabwareValidatorFactory lwValidatorFactory;
    private final WorkService workService;
    private final CommentValidationService commentValidationService;
    private final StoreService storeService;
    private final Transactor transactor;

    private final LabwareService lwService;
    private final BioRiskService bioRiskService;
    private final OperationService opService;
    private final LabwareRepo lwRepo;
    private final BioStateRepo bsRepo;
    private final FixativeRepo fixRepo;
    private final LabwareTypeRepo lwTypeRepo;
    private final TissueRepo tissueRepo;
    private final SampleRepo sampleRepo;
    private final SlotRepo slotRepo;
    private final OperationTypeRepo opTypeRepo;
    private final OperationCommentRepo opComRepo;

    public PotProcessingServiceImp(LabwareValidatorFactory lwValidatorFactory, WorkService workService,
                                   CommentValidationService commentValidationService, StoreService storeService,
                                   Transactor transactor, LabwareService lwService, BioRiskService bioRiskService, OperationService opService,
                                   LabwareRepo lwRepo, BioStateRepo bsRepo, FixativeRepo fixRepo,
                                   LabwareTypeRepo lwTypeRepo, TissueRepo tissueRepo, SampleRepo sampleRepo,
                                   SlotRepo slotRepo, OperationTypeRepo opTypeRepo, OperationCommentRepo opComRepo) {
        this.lwValidatorFactory = lwValidatorFactory;
        this.workService = workService;
        this.commentValidationService = commentValidationService;
        this.storeService = storeService;
        this.transactor = transactor;
        this.lwService = lwService;
        this.opService = opService;
        this.lwRepo = lwRepo;
        this.bsRepo = bsRepo;
        this.fixRepo = fixRepo;
        this.lwTypeRepo = lwTypeRepo;
        this.tissueRepo = tissueRepo;
        this.sampleRepo = sampleRepo;
        this.slotRepo = slotRepo;
        this.opTypeRepo = opTypeRepo;
        this.opComRepo = opComRepo;
        this.bioRiskService = bioRiskService;
    }

    @Override
    public OperationResult perform(User user, PotProcessingRequest request) throws ValidationException {
        OperationResult opres = transactor.transact("Pot processing", () -> performInTransaction(user, request));
        if (request.isSourceDiscarded()) {
            storeService.discardStorage(user, List.of(request.getSourceBarcode()));
        }
        return opres;
    }

    /**
     * Validate and record the request. This should be called within a transaction.
     * @param user the user responsible
     * @param request the request
     * @return the labware and operations created
     * @exception ValidationException validation fails
     */
    public OperationResult performInTransaction(User user, PotProcessingRequest request) throws ValidationException {
        requireNonNull(user, "User is null.");
        requireNonNull(request, "Request is null.");
        final Collection<String> problems = new LinkedHashSet<>();

        Labware source = loadSource(problems, request.getSourceBarcode());
        Work work;
        if (nullOrEmpty(request.getWorkNumber())) {
            problems.add("No work number was supplied.");
            work = null;
        } else {
            work = workService.validateUsableWork(problems, request.getWorkNumber());
        }

        if (request.getDestinations().isEmpty()) {
            problems.add("No destinations specified.");
            throw new ValidationException("The request could not be validated.", problems);
        }

        UCMap<Fixative> fixatives = loadFixatives(problems, request);
        UCMap<LabwareType> lwTypes = loadLabwareTypes(problems, request);
        Map<Integer, Comment> comments = loadComments(problems, request.getDestinations());
        checkFixatives(problems, request, fixatives);

        if (!problems.isEmpty()) {
            throw new ValidationException("The request could not be validated.", problems);
        }

        return record(user, request, source, fixatives, lwTypes, comments, work);
    }

    /**
     * Loads and checks the source labware.
     * @param problems receptacle for problems
     * @param barcode barcode of source labware
     * @return the labware loaded, if any
     */
    public Labware loadSource(Collection<String> problems, String barcode) {
        if (nullOrEmpty(barcode)) {
            problems.add("No source barcode was supplied.");
            return null;
        }
        BioState bs = bsRepo.getByName("Original sample");
        LabwareValidator val = lwValidatorFactory.getValidator();
        var lws = val.loadLabware(lwRepo, List.of(barcode));
        val.validateSources();
        val.validateBioState(bs);
        problems.addAll(val.getErrors());
        if (lws.isEmpty()) {
            return null;
        }
        Labware lw = lws.getFirst();
        final Address A1 = new Address(1, 1);
        if (lw.getSlots().stream().anyMatch(slot -> !slot.getSamples().isEmpty() && !slot.getAddress().equals(A1))) {
            problems.add("Source labware should not have samples in slots other than the first.");
        }
        return lw;
    }

    /**
     * Loads the fixatives specified in the request
     * @param problems receptacle for problems
     * @param request the request
     * @return map of fixatives from their names
     */
    public UCMap<Fixative> loadFixatives(Collection<String> problems, PotProcessingRequest request) {
        return loadFromStrings(problems, request.getDestinations(),
                fixRepo::findAllByNameIn, PotProcessingDestination::getFixative,
                Fixative::getName, "Fixative name");
    }

    /**
     * Loads the labware types specified in the request
     * @param problems receptacle for problems
     * @param request the request
     * @return map of labware types from their names
     */
    public UCMap<LabwareType> loadLabwareTypes(Collection<String> problems, PotProcessingRequest request) {
        return loadFromStrings(problems, request.getDestinations(),
                lwTypeRepo::findAllByNameIn, PotProcessingDestination::getLabwareType,
                LabwareType::getName, "Labware type name");
    }

    /**
     * Loads entities from strings
     * @param problems receptacle for problems
     * @param reqs the parts of the request specifying the entities to load
     * @param lkpFn function to look up entities from multiple strings
     * @param reqFn function to get strings from request parts
     * @param entityFn function to get string from entity
     * @param fieldName name of field being looked up
     * @return map of entities from the relevant strings
     * @param <R> type of the part of the request
     * @param <E> type of entity being loaded
     */
    private <R, E> UCMap<E> loadFromStrings(Collection<String> problems,
                                            Collection<R> reqs,
                                            Function<Collection<String>, ? extends Collection<E>> lkpFn,
                                            Function<R, String> reqFn,
                                            Function<E, String> entityFn,
                                            String fieldName) {
        boolean anyMissing = false;
        Set<String> strings = new LinkedHashSet<>();
        for (var req : reqs) {
            String string = reqFn.apply(req);
            if (nullOrEmpty(string)) {
                anyMissing = true;
            } else {
                strings.add(string);
            }
        }
        if (anyMissing) {
            problems.add(fieldName+" missing.");
        }
        if (strings.isEmpty()) {
            return new UCMap<>();
        }
        UCMap<E> entities = UCMap.from(lkpFn.apply(strings), entityFn);
        List<String> unknown = strings.stream()
                .filter(string -> entities.get(string)==null)
                .map(BasicUtils::repr)
                .toList();
        if (!unknown.isEmpty()) {
            problems.add(fieldName+" unknown: "+unknown);
        }
        return entities;
    }

    /**
     * Checks that the fixatives are appropriate for the given labware types.
     * The fetal waste labware is not supposed to have a fixative (other than None)
     * @param problems receptacle for problems
     * @param request the request
     * @param fixatives the map of fixatives from their names
     */
    public void checkFixatives(Collection<String> problems, PotProcessingRequest request, UCMap<Fixative> fixatives) {
        if (request.getDestinations().stream()
                .anyMatch(dest -> {
                    Fixative fix = dest.getFixative()==null ? null : fixatives.get(dest.getFixative());
                    return (isForFetalWaste(dest) && fix!=null && !fix.getName().equalsIgnoreCase("None"));
                })) {
            problems.add("A fixative is not expected for fetal waste labware.");
        }
    }

    /**
     * Loads the comments specified in the request
     * @param problems receptacle for problems
     * @param destinations the destinations specified in the request
     * @return a map of comments from their ids
     */
    public Map<Integer, Comment> loadComments(Collection<String> problems,
                                              Collection<PotProcessingDestination> destinations) {
        var commentIds = destinations.stream()
                .map(PotProcessingDestination::getCommentId)
                .filter(Objects::nonNull);
        return commentValidationService.validateCommentIds(problems, commentIds)
                .stream()
                .collect(BasicUtils.inMap(Comment::getId));
    }

    /**
     * Creates the labware and operations requested. Records comments and links ops to work.
     * @param user the user responsible
     * @param request the request
     * @param source the source labware
     * @param fixatives the map of fixatives from their names
     * @param lwTypes the map of labware types from their names
     * @param comments the map of comments from their ids
     * @param work the work
     * @return the labware and operations created
     */
    public OperationResult record(User user, PotProcessingRequest request, Labware source, UCMap<Fixative> fixatives,
                                  UCMap<LabwareType> lwTypes, Map<Integer, Comment> comments, Work work) {
        List<Sample> sourceSamples = source.getFirstSlot().getSamples();
        Map<TissueFixKey, Tissue> tissues = tissueFixToTissue(sourceSamples, fixatives.values());
        List<List<Sample>> destSamples = createSamplesPerDestination(request.getDestinations(), sourceSamples, tissues);
        List<Labware> dests = createDestinations(request.getDestinations(), lwTypes, destSamples);
        List<Operation> ops = createOps(request.getDestinations(), destSamples, user, source, sourceSamples, dests, comments);
        if (request.isSourceDiscarded()) {
            source.setDiscarded(true);
            lwRepo.save(source);
        }
        bioRiskService.copyOpSampleBioRisks(ops);
        if (work!=null) {
            workService.link(work, ops);
        }
        return new OperationResult(ops, dests);
    }

    /**
     * Creates tissues correct for each specified fixative.
     * @param samples the original samples
     * @param fixatives the fixatives that the tissues must have
     * @return a map of tissue id and fixative name to tissue
     */
    Map<TissueFixKey, Tissue> tissueFixToTissue(Collection<Sample> samples, Collection<Fixative> fixatives) {
        Map<TissueFixKey, Tissue> tissueFixToTissue = new HashMap<>();
        Set<Integer> doneTissues = new HashSet<>();
        for (Sample sample : samples) {
            Tissue tissue = sample.getTissue();
            if (doneTissues.add(tissue.getId())) {
                for (Fixative fix : fixatives) {
                    final TissueFixKey key = new TissueFixKey(tissue, fix.getName());
                    if (fix.equals(tissue.getFixative())) {
                        tissueFixToTissue.put(key, tissue);
                    } else if (tissueFixToTissue.get(key)==null) {
                        tissueFixToTissue.put(key, createTissue(tissue, fix));
                    }
                }
            }
        }
        return tissueFixToTissue;
    }

    /**
     * Creates a new tissue derived from the given original tissue, with the given fixative.
     * @param ogTissue the original tissue
     * @param fixative the fixative for the new tissue
     * @return a new tissue, created in the database
     */
    public Tissue createTissue(Tissue ogTissue, Fixative fixative) {
        Tissue newTissue = ogTissue.derived();
        newTissue.setFixative(fixative);
        return tissueRepo.save(newTissue);
    }

    /**
     * Is the given request destination targeting fetal waste labware?
     * @param dest a request destination
     * @return true if the destination specifies fetal waste labware type.
     */
    public boolean isForFetalWaste(PotProcessingDestination dest) {
        String ltName = dest.getLabwareType();
        return (ltName !=null && ltName.equalsIgnoreCase(LabwareType.FETAL_WASTE_NAME));
    }

    /**
     * Creates a new sample
     * @param tissue the tissue for the sample
     * @param bs the bio state for the tissue
     * @return a new sample, created in the database
     */
    public Sample createSample(Tissue tissue, BioState bs) {
        Sample newSample = new Sample(null, null, tissue, bs);
        return sampleRepo.save(newSample);
    }

    /**
     * Creates a list of samples for each destination
     * One sample will be created for each combination of tissue and bio state.
     * Original samples will be used in place of a sample with the same tissue and bio state.
     * @param destinations the request destinations
     * @param ogSamples the original samples
     * @param tissues the map of tissues to use for each fixative name
     * @return a list of maps from source sample id to dest sample. The list is in the order corresponding to the destinations
     */
    List<List<Sample>> createSamplesPerDestination(Collection<PotProcessingDestination> destinations,
                                                   Collection<Sample> ogSamples,
                                                   Map<TissueFixKey, Tissue> tissues) {
        Map<TissueBSKey, Sample> sampleMap = new HashMap<>();
        for (Sample sample : ogSamples) {
            sampleMap.put(new TissueBSKey(sample.getTissue(), sample.getBioState()), sample);
        }
        List<List<Sample>> samplesPerDest = new ArrayList<>(destinations.size());
        BioState fwBs = null;
        for (PotProcessingDestination dest : destinations) {
            boolean fw = isForFetalWaste(dest);
            if (fw && fwBs == null) {
                fwBs = bsRepo.getByName("Fetal waste");
            }
            List<Sample> destSampleList = new ArrayList<>(ogSamples.size());
            for (Sample ogSample : ogSamples) {
                BioState bs = fw ? fwBs : ogSample.getBioState();
                Tissue destTissue = tissues.get(new TissueFixKey(ogSample.getTissue(), dest.getFixative()));
                TissueBSKey key = new TissueBSKey(destTissue, bs);
                Sample destSample = sampleMap.get(key);
                if (destSample==null) {
                    destSample = createSample(destTissue, bs);
                    sampleMap.put(key, destSample);
                }
                destSampleList.add(destSample);
            }
            samplesPerDest.add(destSampleList);
        }
        return samplesPerDest;
    }

    /**
     * Creates the destination labware
     * @param destinations the request destinations
     * @param lwTypes the labware types, mapped from their names
     * @param samples the samples, in the corresponding order to the destinations
     * @return a list of labware, in corresponding order, containing the given samples
     */
    public List<Labware> createDestinations(List<PotProcessingDestination> destinations,
                                            UCMap<LabwareType> lwTypes, List<List<Sample>> samples) {
        List<Labware> labware = destinations.stream()
                .map(dest -> lwService.create(lwTypes.get(dest.getLabwareType())))
                .collect(toList());
        Zip.of(labware.stream(), samples.stream()).forEach((lw, sams) -> {
            Slot slot = lw.getFirstSlot();
            slot.getSamples().addAll(sams);
            slotRepo.save(slot);
        });
        return labware;
    }

    /**
     * Creates operations for the request
     * @param destinations the request destinations
     * @param destSampleLists the samples in the corresponding order to the destinations
     * @param user the user responsible for the operations
     * @param source the source labware
     * @param sourceSamples the samples in the source labware
     * @param labware the destination labware
     * @param comments map to look up comments from id
     * @return list of operations in corresponding order
     */
    public List<Operation> createOps(List<PotProcessingDestination> destinations,
                                     List<List<Sample>> destSampleLists, User user, Labware source,
                                     List<Sample> sourceSamples,
                                     Collection<Labware> labware, Map<Integer, Comment> comments) {
        Slot srcSlot = source.getFirstSlot();
        final List<Operation> ops = new ArrayList<>(destinations.size());
        final List<OperationComment> opComs = new ArrayList<>();
        OperationType opType = opTypeRepo.getByName("Pot processing");
        Iterator<Labware> lwIter = labware.iterator();
        Iterator<List<Sample>> destSamplesIter = destSampleLists.iterator();
        for (var dest : destinations) {
            Labware lw = lwIter.next();
            List<Sample> destSamples = destSamplesIter.next();
            Operation op = createOp(opType, user, srcSlot, lw.getFirstSlot(), sourceSamples, destSamples);
            if (dest.getCommentId()!=null) {
                Comment comment = comments.get(dest.getCommentId());
                opComs.add(new OperationComment(null, comment, op.getId(), null, null, lw.getId()));
            }
            ops.add(op);
        }
        if (!opComs.isEmpty()) {
            opComRepo.saveAll(opComs);
        }
        return ops;
    }

    /**
     * Creates an operation
     * @param opType the operation type
     * @param user the user responsible
     * @param srcSlot the source slot
     * @param destSlot the destination slot
     * @param srcSamples the source samples
     * @param destSamples the destination samples (parallel to source samples)
     * @return a new operation created in the database with one action, as specified
     */
    public Operation createOp(OperationType opType, User user, Slot srcSlot, Slot destSlot,
                              List<Sample> srcSamples, List<Sample> destSamples) {
        List<Action> actions = Zip.of(srcSamples.stream(), destSamples.stream())
                .map((srcSam, destSam) -> new Action(null, null, srcSlot, destSlot, destSam, srcSam))
                .toList();
        return opService.createOperation(opType, user, actions, null);
    }

    /** A key indicating a tissue and a bio state */
    record TissueBSKey(Integer tissueId, Integer bsId) {
        TissueBSKey(Tissue tissue, BioState bs) {
            this(tissue.getId(), bs.getId());
        }
    }

    /** A key indicating a tissue and a fixative name */
    record TissueFixKey(Integer tissueId, String fixativeName) {
        TissueFixKey(Tissue tissue, String fixativeName) {
            this(tissue.getId(), fixativeName);
        }
        TissueFixKey(Integer tissueId, String fixativeName) {
            this.tissueId = tissueId;
            this.fixativeName = fixativeName.toUpperCase();
        }
    }
}
