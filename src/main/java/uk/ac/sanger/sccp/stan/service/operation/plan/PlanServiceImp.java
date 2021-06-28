package uk.ac.sanger.sccp.stan.service.operation.plan;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.plan.*;
import uk.ac.sanger.sccp.stan.service.LabwareService;
import uk.ac.sanger.sccp.stan.service.ValidationException;

import javax.persistence.EntityNotFoundException;
import java.util.*;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * @author dr6
 */
@Service
public class PlanServiceImp implements PlanService {
    private final PlanValidationFactory planValidationFactory;
    private final LabwareService lwService;
    private final PlanOperationRepo planRepo;
    private final PlanActionRepo planActionRepo;
    private final OperationTypeRepo opTypeRepo;
    private final LabwareRepo lwRepo;
    private final LabwareTypeRepo ltRepo;

    @Autowired
    public PlanServiceImp(PlanValidationFactory planValidationFactory,
                          LabwareService lwService,
                          PlanOperationRepo planRepo, PlanActionRepo planActionRepo,
                          OperationTypeRepo opTypeRepo, LabwareRepo lwRepo, LabwareTypeRepo ltRepo) {
        this.planValidationFactory = planValidationFactory;
        this.lwService = lwService;
        this.planRepo = planRepo;
        this.planActionRepo = planActionRepo;
        this.opTypeRepo = opTypeRepo;
        this.lwRepo = lwRepo;
        this.ltRepo = ltRepo;
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

    public PlanResult executePlanRequest(User user, PlanRequest request) {
        // At some point it may be necessary to derive a new biostate as part of this method
        PlanOperation plan = createPlan(user, request.getOperationType());
        Map<String, Labware> sources = lookUpSources(request);
        List<Labware> destinations = createDestinations(request);
        List<PlanAction> actions = createActions(request, plan.getId(), sources, destinations, null);
        plan.setPlanActions(actions);
        return new PlanResult(List.of(plan), destinations);
    }

    public PlanOperation createPlan(User user, String opTypeName) {
        OperationType opType = opTypeRepo.getByName(opTypeName);
        PlanOperation plan = new PlanOperation();
        plan.setUser(user);
        plan.setOperationType(opType);
        planRepo.save(plan);
        return plan;
    }

    public Map<String, Labware> lookUpSources(PlanRequest request) {
        // validation has already confirmed that the source labware exist
        return request.getLabware().stream()
                .flatMap(lw -> lw.getActions().stream())
                .map(ac -> ac.getSource().getBarcode().toUpperCase())
                .distinct()
                .collect(toMap(bc -> bc, lwRepo::getByBarcode));
    }

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

    public List<PlanAction> createActions(PlanRequest request, int planId,
                                          Map<String, Labware> sources, List<Labware> destinations,
                                          BioState newBioState) {
        assert request.getLabware().size()==destinations.size();
        Iterator<Labware> destIter = destinations.iterator();
        List<PlanAction> actions = new ArrayList<>();

        for (PlanRequestLabware prlw : request.getLabware()) {
            Labware dest = destIter.next();
            for (PlanRequestAction prac : sortedActions(prlw.getActions())) {
                Labware source = sources.get(prac.getSource().getBarcode().toUpperCase());
                Slot slot0;
                if (prac.getSource().getAddress()!=null) {
                    slot0 = source.getSlot(prac.getSource().getAddress());
                } else {
                    slot0 = source.getFirstSlot();
                }
                Slot slot1 = dest.getSlot(prac.getAddress());
                Sample originalSample = slot0.getSamples().stream()
                        .filter(sample -> sample.getId() == prac.getSampleId())
                        .findAny()
                        .orElseThrow(() -> new EntityNotFoundException("Sample " + prac.getSampleId()
                                + " not found in " + prac.getSource()));
                PlanAction action = new PlanAction(null, planId, slot0, slot1, originalSample,
                        null, prac.getSampleThickness(), newBioState);
                actions.add(planActionRepo.save(action));
            }
        }
        return actions;
    }

    private static List<PlanRequestAction> sortedActions(List<PlanRequestAction> pracs) {
        return pracs.stream()
                .sorted(Comparator.comparing(PlanRequestAction::getAddress, Address.COLUMN_MAJOR))
                .collect(toList());
    }
}
