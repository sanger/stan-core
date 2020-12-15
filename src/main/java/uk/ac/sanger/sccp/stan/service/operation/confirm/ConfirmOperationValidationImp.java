package uk.ac.sanger.sccp.stan.service.operation.confirm;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.confirm.ConfirmOperationLabware;
import uk.ac.sanger.sccp.stan.request.confirm.ConfirmOperationLabware.AddressCommentId;
import uk.ac.sanger.sccp.stan.request.confirm.ConfirmOperationRequest;

import java.util.*;
import java.util.function.Function;

import static java.util.stream.Collectors.*;

/**
 * Tool for validating a {@link ConfirmOperationRequest}.
 * @author dr6
 */
public class ConfirmOperationValidationImp implements ConfirmOperationValidation {
    private final Collection<String> problems = new LinkedHashSet<>();
    private final ConfirmOperationRequest request;
    private final LabwareRepo labwareRepo;
    private final PlanOperationRepo planOpRepo;
    private final CommentRepo commentRepo;

    public ConfirmOperationValidationImp(ConfirmOperationRequest request,
                                         LabwareRepo labwareRepo, PlanOperationRepo planOpRepo, CommentRepo commentRepo) {
        this.request = request;
        this.labwareRepo = labwareRepo;
        this.planOpRepo = planOpRepo;
        this.commentRepo = commentRepo;
    }

    @Override
    public Collection<String> validate() {
        if (request.getLabware().isEmpty()) {
            addProblem("No labware specified in request.");
            return problems;
        }
        Map<String, Labware> labware = validateLabware();
        Map<Integer, PlanOperation> plans = lookUpPlans(labware.values());
        validateOperations(labware, plans);
        validateComments();
        return problems;
    }

    /**
     * Looks up the labware specified in the request; adds any problems
     * @return a map of the loaded labware from its barcode
     */
    public Map<String, Labware> validateLabware() {
        Map<String, Labware> labware = new HashMap<>(request.getLabware().size());
        Set<String> seenBarcodes = new HashSet<>(labware.size());
        for (ConfirmOperationLabware col : request.getLabware()) {
            if (col.getBarcode()==null || col.getBarcode().isEmpty()) {
                addProblem("Missing labware barcode.");
                continue;
            }
            String bc = col.getBarcode().toUpperCase();
            if (!seenBarcodes.add(bc)) {
                addProblem("Repeated labware barcode: "+col.getBarcode());
                continue;
            }
            Optional<Labware> optLw = labwareRepo.findByBarcode(bc);
            if (optLw.isEmpty()) {
                addProblem("Unknown labware barcode: "+col.getBarcode());
                continue;
            }
            Labware lw = optLw.get();
            if (lw.isDiscarded()) {
                addProblem("Labware %s is already discarded.", col.getBarcode());
            }
            if (lw.getSlots().stream().anyMatch(slot -> !slot.getSamples().isEmpty())) {
                addProblem("Labware %s already has contents.", col.getBarcode());
            }
            labware.put(bc, lw);
        }
        return labware;
    }

    /**
     * Looks up the plans for the indicated labware.
     * Adds any problems. If there is no plan for a piece of labware, or if there are multiple plans for a piece of
     * labware, those both qualify as problems.
     * @param labware the labware to look up plans for
     * @return a map of labware id to the plan for that labware.
     */
    public Map<Integer, PlanOperation> lookUpPlans(Collection<Labware> labware) {
        Map<Integer, Labware> labwareByIds = labware.stream()
                .collect(toMap(Labware::getId, Function.identity()));
        Map<Integer, Set<PlanOperation>> labwarePlans = new HashMap<>(labwareByIds.size());
        for (PlanOperation plan : planOpRepo.findAllByDestinationIdIn(labwareByIds.keySet())) {
            for (PlanAction action : plan.getPlanActions()) {
                Integer lwId = action.getDestination().getLabwareId();
                if (!labwareByIds.containsKey(lwId)) {
                    continue;
                }
                labwarePlans.computeIfAbsent(lwId, k -> new HashSet<>()).add(plan);
            }
        }
        Map<Integer, PlanOperation> labwarePlan = new HashMap<>(labwarePlans.size());
        for (Labware lw : labwareByIds.values()) {
            Integer lwId = lw.getId();
            Set<PlanOperation> plans = labwarePlans.get(lwId);
            if (plans==null || plans.isEmpty()) {
                addProblem("No plan found for labware "+lw.getBarcode());
            } else if (plans.size()==1) {
                labwarePlan.put(lwId, plans.iterator().next());
            } else {
                addProblem("Multiple plans found for labware "+lw.getBarcode());
            }
        }
        return labwarePlan;
    }

    /**
     * Performs some checks on the plans for each labware.
     * Adds any problems found
     * @param labware the map of labware from its barcode
     * @param labwarePlans the map of labware id to its plan
     */
    public void validateOperations(Map<String, Labware> labware, Map<Integer, PlanOperation> labwarePlans) {
        for (ConfirmOperationLabware col : request.getLabware()) {
            if (col.getBarcode()==null || col.getBarcode().isEmpty()) {
                continue;
            }
            Labware lw = labware.get(col.getBarcode().toUpperCase());
            if (lw!=null) {
                PlanOperation plan = labwarePlans.get(lw.getId());
                if (plan!=null) {
                    validateAddresses(col, lw, plan);
                }
            }
        }
    }

    /**
     * Validates the addresses for one confirm-labware request.
     * Adds any problems found.
     * This should not be called when the labware or the plan is not found.
     * @param col the confirm-labware request
     * @param lw the labware for this request
     * @param plan the plan for this request
     */
    public void validateAddresses(ConfirmOperationLabware col, Labware lw, PlanOperation plan) {
        Set<Address> planAddresses = plan.getPlanActions().stream()
                .filter(planAction -> planAction.getDestination().getLabwareId().equals(lw.getId()))
                .map(planAction -> planAction.getDestination().getAddress())
                .collect(toSet());
        LabwareType lt = lw.getLabwareType();
        for (Address ad : col.getCancelledAddresses()) {
            if (lt.indexOf(ad) < 0) {
                addProblem("Invalid address %s in cancelled addresses for labware %s.", ad, lw.getBarcode());
            } else if (!planAddresses.contains(ad)) {
                addProblem("No planned action recorded for address %s in labware %s, specified as cancelled.",
                        ad, lw.getBarcode());
            }
        }
        for (AddressCommentId adcom : col.getAddressComments()) {
            Address ad = adcom.getAddress();
            if (ad==null) {
                addProblem("Comment specified with no address for labware %s.", lw.getBarcode());
                continue;
            }
            if (lt.indexOf(ad) < 0) {
                addProblem("Invalid address %s in comments for labware %s.", ad, lw.getBarcode());
            } else if (!planAddresses.contains(ad)) {
                addProblem("No planned action recorded for address %s in labware %s, specified in comments.",
                        ad, lw.getBarcode());
            }
        }
    }

    /**
     * Validates that the comment ids in the request are all valid.
     * Adds a problem for any that are invalid or null.
     */
    public void validateComments() {
        Set<Integer> commentIds = request.getLabware().stream()
                .flatMap(col -> col.getAddressComments().stream().map(AddressCommentId::getCommentId))
                .collect(toCollection(HashSet::new));
        if (commentIds.contains(null)) {
            addProblem("Null given as ID for comment.");
            commentIds.remove(null);
        }
        if (commentIds.isEmpty()) {
            return;
        }
        Set<Integer> foundCommentIds = commentRepo.findIdByIdIn(commentIds);
        commentIds.removeAll(foundCommentIds);
        if (!commentIds.isEmpty()) {
            addProblem("Unknown comment IDs: "+commentIds);
        }
    }

    /**
     * Gets the problems in this validation object.
     * @return the collection of problems added in various validation methods
     */
    public Collection<String> getProblems() {
        return this.problems;
    }

    private void addProblem(String problem) {
        this.problems.add(problem);
    }

    private void addProblem(String format, Object... args) {
        addProblem(String.format(format, args));
    }
}
