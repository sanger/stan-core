package uk.ac.sanger.sccp.stan.service.operation.confirm;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.LabwareRepo;
import uk.ac.sanger.sccp.stan.repo.PlanOperationRepo;
import uk.ac.sanger.sccp.stan.request.confirm.*;
import uk.ac.sanger.sccp.stan.request.confirm.ConfirmSectionLabware.AddressCommentId;
import uk.ac.sanger.sccp.stan.service.CommentValidationService;
import uk.ac.sanger.sccp.stan.service.SlotRegionService;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * @author dr6
 */
@Service
public class ConfirmSectionValidationServiceImp implements ConfirmSectionValidationService {
    private final LabwareRepo labwareRepo;
    private final PlanOperationRepo planOpRepo;
    private final WorkService workService;
    private final SlotRegionService slotRegionService;
    private final CommentValidationService commentValidationService;

    @Autowired
    public ConfirmSectionValidationServiceImp(LabwareRepo labwareRepo, PlanOperationRepo planOpRepo,
                                              WorkService workService, SlotRegionService slotRegionService,
                                              CommentValidationService commentValidationService) {
        this.labwareRepo = labwareRepo;
        this.planOpRepo = planOpRepo;
        this.workService = workService;
        this.slotRegionService = slotRegionService;
        this.commentValidationService = commentValidationService;
    }

    @Override
    public ConfirmSectionValidation validate(ConfirmSectionRequest request) {
        requireNonNull(request, "Request is null");
        final Set<String> problems = new LinkedHashSet<>();
        if (request.getLabware()==null || request.getLabware().isEmpty()) {
            problems.add("No labware specified in request.");
            return new ConfirmSectionValidation(problems);
        }
        UCMap<Labware> labware = validateLabware(problems, request.getLabware());
        UCMap<SlotRegion> slotRegions = validateSlotRegions(problems, request.getLabware());
        Map<Integer, PlanOperation> plans = lookUpPlans(problems, labware.values());
        validateOperations(problems, request.getLabware(), labware, plans);
        Map<Integer, Comment> commentIdMap = validateCommentIds(problems, request.getLabware());
        workService.validateUsableWork(problems, request.getWorkNumber());
        if (!problems.isEmpty()) {
            return new ConfirmSectionValidation(problems);
        }
        return new ConfirmSectionValidation(labware, plans, slotRegions, commentIdMap);
    }

    public Map<Integer, Comment> validateCommentIds(Set<String> problems, List<ConfirmSectionLabware> csls) {
        Stream<Integer> slotCommentIds = csls.stream().flatMap(csl -> csl.getAddressComments().stream())
                .map(AddressCommentId::getCommentId);
        Stream<Integer> sampleCommentIds = csls.stream().flatMap(csl -> csl.getConfirmSections().stream())
                .flatMap(cs -> cs.getCommentIds().stream());
        Stream<Integer> commentIds = Stream.concat(slotCommentIds, sampleCommentIds).filter(Objects::nonNull);
        return commentValidationService.validateCommentIds(problems, commentIds).stream()
                .collect(BasicUtils.toMap(Comment::getId));
    }

    /**
     * Loads and checks the labware specified in the request.
     * @param problems receptacle for problems found
     * @param labware the labware parts of the request
     * @return the loaded labware, mapped from barcode
     */
    public UCMap<Labware> validateLabware(Collection<String> problems, List<ConfirmSectionLabware> labware) {
        UCMap<Labware> lwMap = new UCMap<>(labware.size());
        Set<String> seenBarcodes = new HashSet<>(labware.size());
        for (var col : labware) {
            final String bc = col.getBarcode();
            if (bc == null || bc.isEmpty()) {
                addProblem(problems, "Missing labware barcode.");
                continue;
            }
            if (!seenBarcodes.add(bc.toUpperCase())) {
                addProblem(problems, "Repeated labware barcode: "+ bc);
                continue;
            }
            Optional<Labware> optLw = labwareRepo.findByBarcode(bc);
            if (optLw.isEmpty()) {
                addProblem(problems, "Unknown labware barcode: "+repr(bc));
                continue;
            }
            Labware lw = optLw.get();
            if (lw.isDestroyed()) {
                addProblem(problems, "Labware %s is destroyed.", bc);
            } else if (lw.isReleased()) {
                addProblem(problems, "Labware %s is released.", bc);
            } else if (lw.isDiscarded()) {
                addProblem(problems, "Labware %s is already discarded.", bc);
            } else if (lw.isUsed()) {
                addProblem(problems, "Labware %s is already used.", bc);
            } else if (!lw.isEmpty()) {
                addProblem(problems, "Labware %s already has contents.", bc);
            }
            lwMap.put(bc, lw);
        }
        return lwMap;
    }

    public UCMap<SlotRegion> validateSlotRegions(Collection<String> problems, List<ConfirmSectionLabware> csls) {
        if (csls.stream().flatMap(csl -> csl.getConfirmSections().stream().map(ConfirmSection::getRegion))
                .allMatch(BasicUtils::nullOrEmpty)) {
            return new UCMap<>(0);
        }
        UCMap<SlotRegion> slotRegions = UCMap.from(asList(slotRegionService.loadSlotRegions(true)),
                SlotRegion::getName);

        for (ConfirmSectionLabware csl : csls) {
            if (nullOrEmpty(csl.getBarcode())) {
                continue;
            }
            Map<Address, Set<SlotRegion>> used = new HashMap<>();
            for (ConfirmSection cs : csl.getConfirmSections()) {
                final String regionName = cs.getRegion();
                if (nullOrEmpty(regionName)) {
                    continue;
                }
                SlotRegion sr = slotRegions.get(regionName);
                if (sr == null) {
                    problems.add("Unknown region: "+repr(regionName));
                    continue;
                }
                final Address address = cs.getDestinationAddress();
                if (address == null) {
                    continue;
                }
                Set<SlotRegion> usedRegions = used.computeIfAbsent(address, k -> new HashSet<>());
                if (!usedRegions.add(sr)) {
                    problems.add(String.format("Region %s specified twice for %s in %s.",
                            sr.getName(), address, csl.getBarcode()));
                }
            }
        }
        return slotRegions;
    }


    /**
     * Looks up the plans for the given labware.
     * Each labware should have exactly one plan, which should be a sectioning plan.
     * @param problems receptacle for any problems found
     * @param labware the labware to look up plans for
     * @return the plans found, mapped from the relevant labware id
     */
    public Map<Integer, PlanOperation> lookUpPlans(Collection<String> problems, Collection<Labware> labware) {
        Set<Integer> labwareIds = labware.stream().map(Labware::getId).collect(toSet());
        Map<Integer, Set<PlanOperation>> lwPlans = new HashMap<>(labwareIds.size());
        for (PlanOperation plan : planOpRepo.findAllByDestinationIdIn(labwareIds)) {
            for (PlanAction ac : plan.getPlanActions()) {
                Integer lwId = ac.getDestination().getLabwareId();
                if (labwareIds.contains(lwId)) {
                    lwPlans.computeIfAbsent(lwId, k -> new HashSet<>()).add(plan);
                }
            }
        }
        Map<Integer, PlanOperation> lwPlan = new HashMap<>(lwPlans.size());
        for (Labware lw : labware) {
            Integer lwId = lw.getId();
            Set<PlanOperation> plans = lwPlans.get(lwId);
            if (plans==null || plans.isEmpty()) {
                addProblem(problems, "No plan found for labware %s.", lw.getBarcode());
            } else if (plans.size() > 1) {
                addProblem(problems, "Multiple plans found for labware %s.", lw.getBarcode());
            } else {
                final PlanOperation plan = plans.iterator().next();
                lwPlan.put(lwId, plan);
                if (!plan.getOperationType().sourceMustBeBlock()) {
                    addProblem(problems, "Expected to find a sectioning plan, but found %s.", plan.getOperationType());
                }
            }
        }
        return lwPlan;
    }

    /**
     * Performs some checks on the plans for each labware.
     * Adds any problems found
     * @param problems the receptacle for validation problems
     * @param cols the list per labware from the confirm request
     * @param labware the map of labware from its barcode
     * @param labwarePlans the map of labware id to its plan
     */
    public void validateOperations(Collection<String> problems, List<ConfirmSectionLabware> cols,
                                   UCMap<Labware> labware, Map<Integer, PlanOperation> labwarePlans) {
        final Map<Integer, Set<Integer>> sampleIdSections = new HashMap<>();
        for (ConfirmSectionLabware col : cols) {
            if (col.getBarcode()==null || col.getBarcode().isEmpty()) {
                continue;
            }
            Labware lw = labware.get(col.getBarcode());
            if (lw!=null) {
                PlanOperation plan = labwarePlans.get(lw.getId());
                if (plan!=null) {
                    validateCommentAddresses(problems, col.getAddressComments(), lw, plan);
                    validateSections(problems, col.getConfirmSections(), lw, plan, sampleIdSections);
                }
            }
        }
    }

    /**
     * Checks the addresses given for comments are valid. I.e. non-null, exist in the labware, and have a planned action
     * @param problems receptacle for problems found
     * @param addressComments the addresses and comment ids requested
     * @param lw the labware for these comments
     * @param plan the plan for the labware
     */
    public void validateCommentAddresses(Collection<String> problems, List<AddressCommentId> addressComments,
                                         Labware lw, PlanOperation plan) {
        if (addressComments.isEmpty()) {
            return;
        }
        final Integer lwId = lw.getId();
        Set<Address> planAddresses = plan.getPlanActions().stream()
                .filter(planAction -> planAction.getDestination().getLabwareId().equals(lwId))
                .map(planAction -> planAction.getDestination().getAddress())
                .collect(toSet());
        LabwareType lt = lw.getLabwareType();
        for (AddressCommentId adcom : addressComments) {
            Address ad = adcom.getAddress();
            if (ad==null) {
                addProblem(problems, "Comment specified with no address for labware %s.", lw.getBarcode());
            } else if (lt.indexOf(ad) < 0) {
                addProblem(problems, "Invalid address %s in comments for labware %s.", ad, lw.getBarcode());
            } else if (!planAddresses.contains(ad)) {
                addProblem(problems, "No planned action recorded for address %s in labware %s, specified in comments.",
                        ad, lw.getBarcode());
            }
        }
    }

    /**
     * Checks the sections for problems.
     * Sample ids and sections must be non-null. The sample id going into each address must have been specified
     * in the plan.
     * The same section of one sample id cannot be used twice. The section number must be greater than
     * the last section already taken from the source block. Section numbers cannot be negative.
     * @param problems receptacle for problems
     * @param cons the sections being confirmed for this item of labware
     * @param lw the item of labware
     * @param plan the plan for the labware
     * @param sampleIdSections a map of sample id to sections specified, which is filled in
     *                         by multiple calls to this method
     */
    public void validateSections(Collection<String> problems, List<ConfirmSection> cons,
                                 Labware lw, PlanOperation plan, final Map<Integer, Set<Integer>> sampleIdSections) {
        // The expected sample ids in each address of lw
        final Map<Address, Set<Integer>> plannedSampleIds = new HashMap<>();
        // The max section id already taken from a given block (from its sample id)
        final Map<Integer, Integer> sampleMaxSection = new HashMap<>();
        for (PlanAction pa : plan.getPlanActions()) {
            final Slot dest = pa.getDestination();
            if (dest.getLabwareId().equals(lw.getId())) {
                final Integer sampleId = pa.getSample().getId();
                plannedSampleIds.computeIfAbsent(dest.getAddress(), ad -> new HashSet<>()).add(sampleId);
                Integer lastSection = pa.getSource().getBlockHighestSection();
                if (lastSection != null) {
                    Integer max = sampleMaxSection.get(sampleId);
                    if (max == null || max < lastSection) {
                        sampleMaxSection.put(sampleId, lastSection);
                    }
                }
            }
        }

        for (ConfirmSection con : cons) {
            validateSection(problems, lw, con, plannedSampleIds, sampleMaxSection, sampleIdSections);
        }
    }

    /**
     * Does the section validation on an individual section in a request.
     * @param problems receptacle for problems found
     * @param lw the labware under consideration
     * @param con the requested section under consideration
     * @param plannedSampleIds the sample ids planned for each address in the labware
     * @param sampleMaxSection the max section already taken from each source sample id (can be null for some sample ids)
     * @param sampleIdSections a map of sample id to sections specified, which is filled in
     *                         by multiple calls to this method
     */
    public void validateSection(Collection<String> problems, Labware lw, ConfirmSection con,
                                Map<Address, Set<Integer>> plannedSampleIds, Map<Integer, Integer> sampleMaxSection,
                                Map<Integer, Set<Integer>> sampleIdSections) {
        boolean ok = true;
        final LabwareType lt = lw.getLabwareType();
        final Address address = con.getDestinationAddress();
        if (address == null) {
            addProblem(problems, "Section specified with no address.");
            ok = false;
        } else if (lt.indexOf(address) < 0) {
            addProblem(problems, "Invalid address %s in labware %s specified as destination.", address, lw.getBarcode());
            ok = false;
        }
        final Integer sampleId = con.getSampleId();
        final Integer section = con.getNewSection();
        if (sampleId == null) {
            addProblem(problems, "Sample id not specified for section.");
            ok = false;
        }
        if (lt.isFetalWaste()) {
            if (section != null) {
                addProblem(problems, "Section number not expected for fetal waste.");
                ok = false;
            }
        } else if (section == null) {
            addProblem(problems, "Section number not specified for section.");
            ok = false;
        } else if (section < 0) {
            addProblem(problems, "Section number cannot be less than zero.");
            ok = false;
        }

        if (!ok) {
            return;
        }

        if (!plannedSampleIds.getOrDefault(address, Set.of()).contains(sampleId)) {
            addProblem(problems, "Sample id %s is not expected in address %s of labware %s.",
                    sampleId, address, lw.getBarcode());
        }

        Set<Integer> sections = sampleIdSections.get(sampleId);
        if (sections == null) {
            sections = new HashSet<>();
            sections.add(section);
            sampleIdSections.put(sampleId, sections);
        } else if (section!=null && sections.contains(section)) {
            addProblem(problems, "Repeated section: %s from sample id %s.", section, sampleId);
        } else {
            sections.add(section);
        }
        Integer maxSection = sampleMaxSection.get(sampleId);
        if (maxSection != null && section != null && section <= maxSection) {
            addProblem(problems, "Section numbers from sample id %s must be greater than %s.", sampleId, maxSection);
        }
    }

    private void addProblem(Collection<String> problems, String problem) {
        problems.add(problem);
    }

    private void addProblem(Collection<String> problems, String format, Object... args) {
        problems.add(String.format(format, args));
    }
}
