package uk.ac.sanger.sccp.stan.service.work;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.Work.SampleSlotId;
import uk.ac.sanger.sccp.stan.model.Work.Status;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.*;
import uk.ac.sanger.sccp.stan.service.Validator;
import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import javax.persistence.EntityNotFoundException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.*;

@Service
public class WorkServiceImp implements WorkService {
    private final ProjectRepo projectRepo;
    private final ProgramRepo programRepo;
    private final CostCodeRepo costCodeRepo;
    private final WorkTypeRepo workTypeRepo;
    private final WorkRepo workRepo;
    private final LabwareRepo lwRepo;
    private final OmeroProjectRepo omeroProjectRepo;
    private final DnapStudyRepo dnapStudyRepo;
    private final ReleaseRecipientRepo recipientRepo;
    private final WorkEventRepo workEventRepo;
    private final WorkEventService workEventService;
    private final Validator<String> priorityValidator;

    @Autowired
    public WorkServiceImp(ProjectRepo projectRepo, ProgramRepo programRepo, CostCodeRepo costCodeRepo,
                          WorkTypeRepo workTypeRepo, WorkRepo workRepo, LabwareRepo lwRepo, OmeroProjectRepo omeroProjectRepo,
                          DnapStudyRepo dnapStudyRepo, ReleaseRecipientRepo recipientRepo, WorkEventRepo workEventRepo, WorkEventService workEventService,
                          @Qualifier("workPriorityValidator") Validator<String> priorityValidator) {
        this.projectRepo = projectRepo;
        this.programRepo = programRepo;
        this.costCodeRepo = costCodeRepo;
        this.workTypeRepo = workTypeRepo;
        this.workRepo = workRepo;
        this.lwRepo = lwRepo;
        this.omeroProjectRepo = omeroProjectRepo;
        this.dnapStudyRepo = dnapStudyRepo;
        this.recipientRepo = recipientRepo;
        this.workEventRepo = workEventRepo;
        this.workEventService = workEventService;
        this.priorityValidator = priorityValidator;
    }

    public void checkPrefix(String prefix) {
        if (prefix==null || prefix.isBlank()) {
            throw new IllegalArgumentException("No prefix supplied for work number.");
        }
        if (!prefix.equalsIgnoreCase("SGP") && !prefix.equalsIgnoreCase("R&D")) {
            throw new IllegalArgumentException("Invalid work number prefix: "+repr(prefix));
        }
    }

    @Override
    public Work createWork(User user, String prefix, String workTypeName, String workRequesterName, String projectName,
                           String programName, String costCode,
                           Integer numBlocks, Integer numSlides, Integer numOriginalSamples,
                           String omeroProjectName, Integer ssStudyId) {
        checkPrefix(prefix);

        Project project = projectRepo.getByName(projectName);
        Program program = programRepo.getByName(programName);
        CostCode cc = costCodeRepo.getByCode(costCode);
        WorkType type = workTypeRepo.getByName(workTypeName);
        OmeroProject omeroProject;
        if (nullOrEmpty(omeroProjectName)) {
            omeroProject = null;
        } else {
            omeroProject = omeroProjectRepo.getByName(omeroProjectName);
            if (!omeroProject.isEnabled()) {
                throw new IllegalArgumentException("Omero project "+omeroProject.getName()+" is disabled.");
            }
        }
        DnapStudy dnapStudy;
        if (ssStudyId==null) {
            dnapStudy = null;
        } else {
            dnapStudy = dnapStudyRepo.getBySsId(ssStudyId);
            if (!dnapStudy.isEnabled()) {
                throw new IllegalArgumentException("DNAP study is disabled: "+dnapStudy);
            }
        }
        if (numBlocks!=null && numBlocks < 0) {
            throw new IllegalArgumentException("Number of blocks cannot be a negative number.");
        }
        if (numSlides!=null && numSlides < 0) {
            throw new IllegalArgumentException("Number of slides cannot be a negative number.");
        }
        if (numOriginalSamples!=null && numOriginalSamples < 0) {
            throw new IllegalArgumentException("Number of original samples cannot be a negative number.");
        }
        requireNonNull(user, "No user supplied.");
        ReleaseRecipient workRequester = findOrCreateRequester(user, workRequesterName);

        String workNumber = workRepo.createNumber(prefix);
        Work work = workRepo.save(new Work(null, workNumber, type, workRequester, project, program, cc, Status.unstarted,
                numBlocks, numSlides, numOriginalSamples, null, omeroProject, dnapStudy));
        workEventService.recordEvent(user, work, WorkEvent.Type.create, null);
        return work;
    }

    /**
     * Looks up the specified work requester. If it doesn't exist as a requester,
     * but matches the given user's username, then creates it.
     * @param user the user responsible for the request
     * @param requesterName the username of the specified work requester
     * @return the work requester
     * @exception IllegalArgumentException if the given string does not correspond to a permitted requester
     */
    public ReleaseRecipient findOrCreateRequester(@NotNull User user, String requesterName) {
        if (nullOrEmpty(requesterName)) {
            throw new IllegalArgumentException("No work requester specified.");
        }
        var optRec = recipientRepo.findByUsername(requesterName);
        if (optRec.isPresent()) {
            return optRec.get();
        }
        if (requesterName.equalsIgnoreCase(user.getUsername())) {
            return recipientRepo.save(new ReleaseRecipient(null, user.getUsername()));
        }
        throw new IllegalArgumentException("Unknown requester: "+repr(requesterName));
    }

    @Override
    public WorkWithComment updateStatus(User user, String workNumber, Status newStatus, Integer commentId) {
        Work work = workRepo.getByWorkNumber(workNumber);
        WorkEvent event = workEventService.recordStatusChange(user, work, newStatus, commentId);
        work.setStatus(newStatus);
        String commentText = (event.getComment()==null ? null : event.getComment().getText());
        if (work.getPriority()!=null && work.isClosed()) { // note: work has new status so isClosed() will work
            work.setPriority(null);
        }
        return new WorkWithComment(workRepo.save(work), commentText);
    }

    @Override
    public Work updateWorkNumBlocks(User user, String workNumber, Integer numBlocks) {
        Work work = workRepo.getByWorkNumber(workNumber);
        if (!Objects.equals(work.getNumBlocks(), numBlocks)) {
            if (numBlocks != null && numBlocks < 0) {
                throw new IllegalArgumentException("Number of blocks cannot be a negative number.");
            }
            work.setNumBlocks(numBlocks);
            work = workRepo.save(work);
        }
        return work;
    }

    @Override
    public Work updateWorkNumSlides(User user, String workNumber, Integer numSlides) {
        Work work = workRepo.getByWorkNumber(workNumber);
        if (!Objects.equals(work.getNumBlocks(), numSlides)) {
            if (numSlides != null && numSlides < 0) {
                throw new IllegalArgumentException("Number of slides cannot be a negative number.");
            }
            work.setNumSlides(numSlides);
            work = workRepo.save(work);
        }
        return work;
    }

    @Override
    public Work updateWorkNumOriginalSamples(User user, String workNumber, Integer numOriginalSamples) {
        Work work = workRepo.getByWorkNumber(workNumber);
        if (!Objects.equals(work.getNumOriginalSamples(), numOriginalSamples)) {
            if (numOriginalSamples != null && numOriginalSamples < 0) {
                throw new IllegalArgumentException("Number of original samples cannot be a negative number.");
            }
            work.setNumOriginalSamples(numOriginalSamples);
            work = workRepo.save(work);
        }
        return work;
    }

    @Override
    public Work updateWorkPriority(User user, String workNumber, String priority) {
        Work work = workRepo.getByWorkNumber(workNumber);
        if (priority==null) {
            if (work.getPriority()!=null) {
                work.setPriority(null);
                work = workRepo.save(work);
            }
        } else {
            priorityValidator.checkArgument(priority);
            priority = priority.toUpperCase();
            if (!priority.equals(work.getPriority())) {
                if (work.isClosed()) {
                    throw new IllegalArgumentException("Cannot set a new priority on "+work.getStatus()+" work.");
                }
                work.setPriority(priority);
                work = workRepo.save(work);
            }
        }
        return work;
    }

    @Override
    public Work updateWorkOmeroProject(User user, String workNumber, String omeroProjectName) {
        Work work = workRepo.getByWorkNumber(workNumber);
        checkAuthorisation(user, work);
        if (omeroProjectName==null) {
            if (work.getOmeroProject()!=null) {
                work.setOmeroProject(null);
                work = workRepo.save(work);
            }
        } else {
            OmeroProject omeroProject = omeroProjectRepo.getByName(omeroProjectName);
            if (work.getOmeroProject()==null || !work.getOmeroProject().equals(omeroProject)) {
                if (!omeroProject.isEnabled()) {
                    throw new IllegalArgumentException("Omero project "+omeroProject.getName()+" is disabled.");
                }
                work.setOmeroProject(omeroProject);
                work = workRepo.save(work);
            }
        }
        return work;
    }

    @Override
    public Work updateWorkDnapStudy(User user, String workNumber, Integer ssStudyId) {
        Work work = workRepo.getByWorkNumber(workNumber);
        checkAuthorisation(user, work);
        if (ssStudyId==null) {
            if (work.getDnapStudy()!=null) {
                work.setDnapStudy(null);
                work = workRepo.save(work);
            }
        } else {
            DnapStudy dnapStudy = dnapStudyRepo.getBySsId(ssStudyId);
            if (work.getDnapStudy()==null || !work.getDnapStudy().equals(dnapStudy)) {
                if (!dnapStudy.isEnabled()) {
                    throw new IllegalArgumentException("DNAP study is disabled: "+dnapStudy);
                }
                work.setDnapStudy(dnapStudy);
                work = workRepo.save(work);
            }
        }
        return work;
    }

    @Override
    public Work link(String workNumber, Collection<Operation> operations) {
        Work work = workRepo.getByWorkNumber(workNumber);
        return link(work, operations, false);
    }

    @Override
    public Work link(Work work, Collection<Operation> operations, boolean evenIfUnusable) {
        if (operations.isEmpty()) {
            return work;
        }
        if (!evenIfUnusable && !work.isUsable()) {
            throw new IllegalArgumentException(work.getWorkNumber()+" cannot be used because it is "+ work.getStatus()+".");
        }
        Set<Integer> opIds = work.getOperationIds();
        Set<SampleSlotId> ssIds = work.getSampleSlotIds();
        for (Operation op : operations) {
            opIds.add(op.getId());
            for (Action action : op.getActions()) {
                SampleSlotId ssId = new SampleSlotId(action.getSample().getId(), action.getDestination().getId());
                ssIds.add(ssId);
            }
        }
        return workRepo.save(work);
    }

    @Override
    public List<Work> linkWorkOps(Stream<WorkOp> workOps) {
        final Map<Integer, Work> workIdMap = new HashMap<>();
        final Map<Integer, List<Operation>> workOpMap = new HashMap<>();
        final List<Work> updatedWorks = new ArrayList<>();
        workOps.forEach(workOp -> {
            Integer workId = workOp.workId();
            if (!workIdMap.containsKey(workId)) {
                workIdMap.put(workId, workOp.work());
                workOpMap.put(workId, new ArrayList<>());
            }
            workOpMap.get(workId).add(workOp.op());
        });
        workOpMap.forEach((workId, ops) -> updatedWorks.add(link(workIdMap.get(workId), ops)));
        return updatedWorks;
    }

    @Override
    public Work linkReleases(Work work, List<Release> releases) {
        if (releases.isEmpty()) {
            return work;
        }
        if (!work.isUsable()) {
            throw new IllegalArgumentException("Work "+work.getWorkNumber()+" is not usable because it is "+work.getStatus().name()+".");
        }
        Set<Integer> releaseIds = work.getReleaseIds();
        Set<SampleSlotId> ssIds = work.getSampleSlotIds();
        for (Release release : releases) {
            releaseIds.add(release.getId());
            for (Slot slot : release.getLabware().getSlots()) {
                for (Sample sample : slot.getSamples()) {
                    ssIds.add(new SampleSlotId(sample.getId(), slot.getId()));
                }
            }
        }
        return workRepo.save(work);
    }

    @Override
    public void link(Collection<Work> works, Collection<Operation> operations) {
        if (operations.isEmpty() || works.isEmpty()) {
            return;
        }
        if (works.size()==1) {
            link(works.iterator().next(), operations);
            return;
        }
        List<String> inactiveWorkNumbers = works.stream()
                .filter(work -> !work.isUsable())
                .map(Work::getWorkNumber)
                .toList();
        if (!inactiveWorkNumbers.isEmpty()) {
            throw new IllegalArgumentException("Specified work cannot be used because it is not active: "+inactiveWorkNumbers);
        }
        Set<Integer> opIds = operations.stream().map(Operation::getId).collect(toSet());
        Set<SampleSlotId> ssIds = operations.stream()
                .flatMap(op -> op.getActions().stream()
                        .map(a -> new SampleSlotId(a.getSample().getId(), a.getDestination().getId())))
                .collect(toSet());

        for (Work work : works) {
            work.getOperationIds().addAll(opIds);
            work.getSampleSlotIds().addAll(ssIds);
        }

        workRepo.saveAll(works);
    }

    @Override
    public Work getUsableWork(String workNumber) {
        requireNonNull(workNumber, "Work number is null.");
        Work work = workRepo.getByWorkNumber(workNumber);
        if (!work.isUsable()) {
            throw new IllegalArgumentException(work.getWorkNumber()+" cannot be used because it is "+work.getStatus()+".");
        }
        return work;
    }

    @Override
    public UCMap<Work> getUsableWorkMap(Collection<String> workNumbers) {
        if (workNumbers.isEmpty()) {
            return new UCMap<>(0);
        }
        if (workNumbers.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("null given as work number.");
        }
        UCMap<Work> workMap = UCMap.from(workRepo.findAllByWorkNumberIn(workNumbers), Work::getWorkNumber);
        Set<String> unknown = new LinkedHashSet<>();
        Set<String> unusable = new LinkedHashSet<>();
        for (String workNumber: workNumbers) {
            Work work = workMap.get(workNumber);
            if (work==null) {
                unknown.add(workNumber.toUpperCase());
            } else if (!work.isUsable()) {
                unusable.add(work.getWorkNumber());
            }
        }
        if (!unknown.isEmpty()) {
            throw new EntityNotFoundException(pluralise("Unknown work number{s}: ", unknown.size()) + unknown);
        }
        if (!unusable.isEmpty()) {
            throw new IllegalArgumentException(pluralise("Inactive work number{s}: ", unusable.size()) + unusable);
        }
        return workMap;
    }

    /**
     * Loads the validates the specified work using the given predicate.
     * Adds a problem if the work is not found, or if it fails the given predicate.
     * @param problems receptacle for problems
     * @param workNumber the work number to load
     * @param predicate a predicate to check against the work (may be null)
     * @return the work corresponding to the given work number, if found
     */
    public Work validateWork(Collection<String> problems, String workNumber, Predicate<Work> predicate) {
        if (nullOrEmpty(workNumber)) {
            problems.add("Work number is not specified.");
            return null;
        }
        Optional<Work> optWork = workRepo.findByWorkNumber(workNumber);
        if (optWork.isEmpty()) {
            problems.add("Work number not recognised: "+repr(workNumber));
            return null;
        }
        Work work = optWork.get();
        if (predicate!=null && !predicate.test(work)) {
            problems.add(work.getWorkNumber()+" cannot be used because it is "+work.getStatus()+".");
        }
        return work;
    }

    @Override
    public Work validateUsableWork(Collection<String> problems, String workNumber) {
        return validateWork(problems, workNumber, Work::isUsable);
    }

    @Override
    public Work validateOpenWork(Collection<String> problems, String workNumber) {
        return validateWork(problems, workNumber, Work::isOpen);
    }

    @Override
    public Work validateWorkForOpType(Collection<String> problems, String workNumber, OperationType opType) {
        if (opType!=null && opType.supportsAnyOpenWork()) {
            return validateOpenWork(problems, workNumber);
        } else {
            return validateUsableWork(problems, workNumber);
        }
    }

    @Override
    public UCMap<Work> validateUsableWorks(Collection<String> problems, Collection<String> workNumbers) {
        return validateWorks(problems, workNumbers, Work::isUsable);
    }

    @Override
    public UCMap<Work> validateWorksForOpType(Collection<String> problems, Collection<String> workNumbers, OperationType opType) {
        final Predicate<Work> predicate = (opType != null && opType.supportsAnyOpenWork()) ? Work::isOpen : Work::isUsable;
        return validateWorks(problems, workNumbers, predicate);
    }

    UCMap<Work> validateWorks(Collection<String> problems, Collection<String> workNumbers, Predicate<Work> predicate) {
        List<String> nonNullWorkNumbers = workNumbers.stream()
                .filter(Objects::nonNull)
                .toList();
        if (nonNullWorkNumbers.isEmpty()) {
            problems.add("No work numbers given.");
            return new UCMap<>(0);
        } else if (nonNullWorkNumbers.size() < workNumbers.size()) {
            problems.add("Work number is not specified.");
        }
        UCMap<Work> workMap = workRepo.findAllByWorkNumberIn(nonNullWorkNumbers).stream()
                .collect(UCMap.toUCMap(Work::getWorkNumber));

        List<String> missing = nonNullWorkNumbers.stream()
                .filter(s -> workMap.get(s)==null)
                .filter(BasicUtils.distinctUCSerial())
                .collect(toList());

        if (!missing.isEmpty()) {
            problems.add((missing.size()==1 ? "Work number" : "Work numbers") +" not recognised: "+
                    BasicUtils.reprCollection(missing));
        }
        if (predicate != null) {
            List<String> unusable = new ArrayList<>();
            Set<String> badStates = new LinkedHashSet<>();
            for (Work work : workMap.values()) {
                if (!predicate.test(work)) {
                    unusable.add(work.getWorkNumber());
                    badStates.add(work.getStatus().name());
                }
            }
            if (!unusable.isEmpty()) {
                String problem = String.format("Work %s cannot be used because %s %s: %s",
                        unusable.size() == 1 ? "number" : "numbers",
                        unusable.size() == 1 ? "it is" : "they are",
                        BasicUtils.commaAndConjunction(badStates, "or"),
                        unusable);
                problems.add(problem);
            }
        }
        return workMap;
    }

    @Override
    public List<WorkWithComment> getWorksWithComments(Collection<Status> statuses) {
        Iterable<Work> works = (statuses==null ? workRepo.findAll() : workRepo.findAllByStatusIn(statuses));
        List<WorkWithComment> wcs = stream(works)
                .map(WorkWithComment::new)
                .collect(toList());
        final Set<Status> pausedOrFailedStatuses = EnumSet.of(Status.paused, Status.failed, Status.withdrawn);
        List<Integer> pausedOrFailedIds = wcs.stream().map(WorkWithComment::getWork)
                .filter(work -> pausedOrFailedStatuses.contains(work.getStatus()))
                .map(Work::getId)
                .collect(toList());
        if (!pausedOrFailedIds.isEmpty()) {
            Map<Integer, WorkEvent> workEvents = workEventService.loadLatestEvents(pausedOrFailedIds);
            fillInComments(wcs, workEvents);
        }
        return wcs;
    }

    @Override
    public SuggestedWorkResponse suggestWorkForLabwareBarcodes(Collection<String> barcodes, boolean includeInactive) {
        Set<Labware> labware = new HashSet<>(lwRepo.getByBarcodeIn(barcodes));
        Map<String, Integer> barcodeWorkIds = new HashMap<>(labware.size());
        Set<Integer> workIds = new HashSet<>();
        Function<Integer, Integer> workIdFn = (includeInactive ? workRepo::findLatestWorkIdForLabwareId
                : workRepo::findLatestActiveWorkIdForLabwareId);
        for (Labware lw : labware) {
            Integer workId = workIdFn.apply(lw.getId());
            barcodeWorkIds.put(lw.getBarcode(), workId);
            if (workId!=null) {
                workIds.add(workId);
            }
        }
        Map<Integer, Work> workIdMap = BasicUtils.stream(workRepo.findAllById(workIds))
                .collect(BasicUtils.inMap(Work::getId));
        List<SuggestedWork> suggestedWorks = barcodeWorkIds.entrySet().stream()
                .map(e -> new SuggestedWork(e.getKey(), e.getValue()==null ? null : workIdMap.get(e.getValue()).getWorkNumber()))
                .collect(toList());
        List<Work> works = new ArrayList<>(workIdMap.values());
        return new SuggestedWorkResponse(suggestedWorks, works);
    }

    @Override
    public List<Labware> suggestLabwareForWorkNumber(String workNumber, boolean forRelease) {
        Work work = workRepo.getByWorkNumber(workNumber);
        List<Integer> possibleLabwareIds = workRepo.findLabwareIdsForWorkIds(List.of(work.getId()));
        if (possibleLabwareIds.isEmpty()) {
            return List.of();
        }
        return lwRepo.findAllByIdIn(possibleLabwareIds).stream()
                .filter(forRelease ? Labware::isReleasable : Labware::isUsable)
                .collect(toList());
    }

    @Override
    public List<Work> getWorksCreatedBy(User user) {
        return workEventRepo.findAllByUserAndType(user, WorkEvent.Type.create)
                .stream()
                .map(WorkEvent::getWork)
                .collect(toList());
    }

    @Override
    public Map<SlotIdSampleId, Set<Work>> loadWorksForSlotsIn(Collection<Labware> labware) {
        return loadWorksForSlots(labware.stream()
                .flatMap(lw -> lw.getSlots().stream()));
    }

    public Map<SlotIdSampleId, Set<Work>> loadWorksForSlots(Stream<Slot> slots) {
        List<Integer> slotIds = slots.map(Slot::getId).toList();
        return workRepo.slotSampleWorksForSlotIds(slotIds);
    }

    public void fillInComments(Collection<WorkWithComment> wcs, Map<Integer, WorkEvent> workEvents) {
        for (WorkWithComment wc : wcs) {
            Work work = wc.getWork();
            WorkEvent event = workEvents.get(work.getId());
            if (event != null && event.getComment()!=null && event.getType().leadsTo(work.getStatus())) {
                wc.setComment(event.getComment().getText());
            }
        }
    }

    /** Check if the given user has permission to update the given work. */
    public void checkAuthorisation(User user, Work work) {
        if (user.hasRole(User.Role.normal)) {
            return;
        }
        if (!user.hasRole(User.Role.enduser)) {
            throw new InsufficientAuthenticationException("User "+user.getUsername()+" does not have privilege to update work.");
        }
        List<WorkEvent> events = workEventRepo.findAllByWorkInAndType(List.of(work), WorkEvent.Type.create);
        if (events.stream().noneMatch(e -> user.equals(e.getUser()))) {
            throw new InsufficientAuthenticationException(String.format(
                    "User %s does not have privilege to update work number %s.",
                    user.getUsername(), work.getWorkNumber()));
        }
    }

}
