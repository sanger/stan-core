package uk.ac.sanger.sccp.stan.service.operation.plan;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.LabwareFlagged;
import uk.ac.sanger.sccp.stan.request.PlanData;
import uk.ac.sanger.sccp.stan.request.plan.*;
import uk.ac.sanger.sccp.stan.service.LabwareService;
import uk.ac.sanger.sccp.stan.service.ValidationException;
import uk.ac.sanger.sccp.stan.service.flag.FlagLookupService;
import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import javax.persistence.EntityNotFoundException;
import java.util.*;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * @author dr6
 */
@Service
public class PlanServiceImp implements PlanService {
    private final PlanValidationFactory planValidationFactory;
    private final LabwareService lwService;
    private final FlagLookupService flagLookupService;
    private final PlanOperationRepo planRepo;
    private final PlanActionRepo planActionRepo;
    private final OperationTypeRepo opTypeRepo;
    private final LabwareRepo lwRepo;
    private final LabwareTypeRepo ltRepo;
    private final LabwareNoteRepo lwNoteRepo;
    private final BioStateRepo bsRepo;

    @Autowired
    public PlanServiceImp(PlanValidationFactory planValidationFactory,
                          LabwareService lwService, FlagLookupService flagLookupService,
                          PlanOperationRepo planRepo, PlanActionRepo planActionRepo,
                          OperationTypeRepo opTypeRepo, LabwareRepo lwRepo, LabwareTypeRepo ltRepo,
                          LabwareNoteRepo lwNoteRepo, BioStateRepo bsRepo) {
        this.planValidationFactory = planValidationFactory;
        this.lwService = lwService;
        this.flagLookupService = flagLookupService;
        this.planRepo = planRepo;
        this.planActionRepo = planActionRepo;
        this.opTypeRepo = opTypeRepo;
        this.lwRepo = lwRepo;
        this.ltRepo = ltRepo;
        this.lwNoteRepo = lwNoteRepo;
        this.bsRepo = bsRepo;
    }

    @Override
    public PlanResult recordPlan(User user, PlanRequest request) throws ValidationException {
        PlanValidation validation = planValidationFactory.createPlanValidation(request);
        Collection<String> problems = validation.validate();
        if (!problems.isEmpty()) {
            throw new ValidationException("The plan request could not be validated.", problems);
        }
        return executePlanRequest(user, request);
    }

    /**
     * Creates the plans as requested
     * @param user the user making the request
     * @param request the request
     * @return new plans and labware as requested
     */
    public PlanResult executePlanRequest(User user, PlanRequest request) {
        UCMap<Labware> sources = lookUpSources(request);
        OperationType opType = opTypeRepo.getByName(request.getOperationType());
        List<Labware> destinations = createDestinations(request);
        BioState opTypeBs = opType.getNewBioState();
        BioState fetalWasteBs = null;
        if (destinations.stream().anyMatch(lw -> lw.getLabwareType().isFetalWaste())) {
            fetalWasteBs = bsRepo.getByName("Fetal waste");
        }
        Iterator<Labware> destIter = destinations.iterator();
        List<PlanOperation> plans = new ArrayList<>(destinations.size());
        List<LabwareNote> lwNotes = new ArrayList<>();
        for (PlanRequestLabware pl : request.getLabware()) {
            PlanOperation plan = createPlan(user, opType);
            Labware lw = destIter.next();
            List<PlanAction> planActions = createActions(pl, plan.getId(), sources, lw,
                    lw.getLabwareType().isFetalWaste() ? fetalWasteBs : opTypeBs);
            plan.setPlanActions(planActions);
            plans.add(plan);
            if (pl.getCosting() != null) {
                lwNotes.add(LabwareNote.noteForPlan(lw.getId(), plan.getId(), "costing", pl.getCosting().name()));
            }
            if (!nullOrEmpty(pl.getLotNumber())) {
                lwNotes.add(LabwareNote.noteForPlan(lw.getId(), plan.getId(), "lot", pl.getLotNumber().toUpperCase()));
            }
        }
        if (!lwNotes.isEmpty()) {
            lwNoteRepo.saveAll(lwNotes);
        }
        return new PlanResult(plans, destinations);
    }

    /**
     * Creates the plan (without any actions as yet)
     * @param user the user responsible for the plan
     * @param opType the operation type of the plan
     * @return the newly created plan (without actions)
     */
    public PlanOperation createPlan(User user, OperationType opType) {
        PlanOperation plan = new PlanOperation();
        plan.setUser(user);
        plan.setOperationType(opType);
        return planRepo.save(plan);
    }

    /**
     * Looks up the source labware for the request
     * @param request the plan request
     * @return a map of the specified source labware from its barcode
     */
    public UCMap<Labware> lookUpSources(PlanRequest request) {
        Set<String> barcodes = request.getLabware().stream()
                .flatMap(rlw -> rlw.getActions().stream())
                .map(ac -> ac.getSource().getBarcode().toUpperCase())
                .collect(toSet());
        return lwRepo.getMapByBarcodeIn(barcodes);
    }

    /**
     * Creates all the destination labware
     * @param request the request specifying what labware to create
     * @return a list of new labware, matching the request
     */
    public List<Labware> createDestinations(PlanRequest request) {
        List<Labware> newLabware = new ArrayList<>(request.getLabware().size());
        LabwareType lt = null;
        for (PlanRequestLabware prlw : request.getLabware()) {
            if (lt==null || !prlw.getLabwareType().equalsIgnoreCase(lt.getName())) {
                lt = ltRepo.getByName(prlw.getLabwareType());
            }
            final Labware lw;
            if (prlw.getBarcode()==null || prlw.getBarcode().isEmpty()) {
                lw = lwService.create(lt);
            } else {
                String externalBarcode = prlw.getBarcode().toUpperCase();
                lw = lwService.create(lt, externalBarcode, externalBarcode);
            }
            newLabware.add(lw);
        }
        return newLabware;
    }

    /**
     * Creates the actions for a plan
     * @param requestLabware the request for one labware in the plan
     * @param planId the id of the plan
     * @param sources a map to look up source labware
     * @param destination the new destination labware
     * @param newBioState the new bio state (if any) for the samples
     * @return a list of newly created actions for the plan
     */
    public List<PlanAction> createActions(PlanRequestLabware requestLabware, int planId,
                                          UCMap<Labware> sources, Labware destination,
                                          BioState newBioState) {
        final List<PlanAction> actions = new ArrayList<>(requestLabware.getActions().size());
        for (PlanRequestAction prac : sortedActions(requestLabware.getActions())) {
            Labware source = sources.get(prac.getSource().getBarcode());
            Slot slot0;
            if (prac.getSource().getAddress()!=null) {
                slot0 = source.getSlot(prac.getSource().getAddress());
            } else {
                slot0 = source.getFirstSlot();
            }
            Slot slot1 = destination.getSlot(prac.getAddress());
            Sample originalSample = slot0.getSamples().stream()
                    .filter(sample -> sample.getId() == prac.getSampleId())
                    .findAny()
                    .orElseThrow(() -> new EntityNotFoundException("Sample " + prac.getSampleId()
                            + " not found in " + prac.getSource()));
            PlanAction action = new PlanAction(null, planId, slot0, slot1, originalSample,
                    null, prac.getSampleThickness(), newBioState);
            actions.add(action);
        }
        return BasicUtils.asList(planActionRepo.saveAll(actions));
    }

    @Override
    public PlanData getPlanData(String barcode, boolean loadFlags) {
        Labware destination = lwRepo.getByBarcode(barcode);
        validateLabwareForPlanData(destination);
        List<PlanOperation> plans = planRepo.findAllByDestinationIdIn(List.of(destination.getId()));
        if (plans.isEmpty()) {
            throw new IllegalArgumentException("No plan found for labware "+destination.getBarcode()+".");
        }
        if (plans.size() > 1) {
            throw new IllegalArgumentException("Multiple plans found for labware "+destination.getBarcode()+".");
        }
        PlanOperation plan = plans.getFirst();
        List<Labware> sources = getSources(plan);
        List<LabwareFlagged> lfSources;
        LabwareFlagged lfDest;
        if (loadFlags) {
            List<Labware> labware = new ArrayList<>(sources.size()+1);
            labware.addAll(sources);
            labware.add(destination);
            List<LabwareFlagged> lf = flagLookupService.getLabwareFlagged(labware);
            lfSources = lf.subList(0, lf.size()-1);
            lfDest = lf.getLast();
        } else {
            lfDest = new LabwareFlagged(destination, false);
            lfSources = sources.stream()
                    .map(lw -> new LabwareFlagged(lw, false))
                    .toList();
        }
        return new PlanData(plan, lfSources, lfDest);
    }

    public void validateLabwareForPlanData(Labware labware) {
        String[] errors = { "already contains samples", "is destroyed", "is released", "is discarded", "is used" };
        List<Predicate<Labware>> predicates = List.of(
                lw -> !lw.isEmpty(),
                Labware::isDestroyed,
                Labware::isReleased,
                Labware::isDiscarded,
                Labware::isUsed
        );
        for (int i = 0; i < errors.length; ++i) {
            if (predicates.get(i).test(labware)) {
                throw new IllegalArgumentException(String.format("Labware %s %s.", labware.getBarcode(), errors[i]));
            }
        }
    }

    public List<Labware> getSources(PlanOperation plan) {
        Set<Integer> labwareIds = plan.getPlanActions().stream()
                .map(pa -> pa.getSource().getLabwareId())
                .collect(toSet());
        return lwRepo.findAllByIdIn(labwareIds);
    }

    private static List<PlanRequestAction> sortedActions(List<PlanRequestAction> pracs) {
        return pracs.stream()
                .sorted(Comparator.comparing(PlanRequestAction::getAddress, Address.COLUMN_MAJOR))
                .collect(toList());
    }
}
