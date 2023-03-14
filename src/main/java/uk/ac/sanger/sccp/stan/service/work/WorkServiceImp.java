package uk.ac.sanger.sccp.stan.service.work;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.util.Streamable;
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

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
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
    private final ReleaseRecipientRepo recipientRepo;
    private final WorkEventService workEventService;
    private final Validator<String> priorityValidator;

    @Autowired
    public WorkServiceImp(ProjectRepo projectRepo, ProgramRepo programRepo, CostCodeRepo costCodeRepo,
                          WorkTypeRepo workTypeRepo, WorkRepo workRepo, LabwareRepo lwRepo, OmeroProjectRepo omeroProjectRepo,
                          ReleaseRecipientRepo recipientRepo, WorkEventService workEventService,
                          @Qualifier("workPriorityValidator") Validator<String> priorityValidator) {
        this.projectRepo = projectRepo;
        this.programRepo = programRepo;
        this.costCodeRepo = costCodeRepo;
        this.workTypeRepo = workTypeRepo;
        this.workRepo = workRepo;
        this.lwRepo = lwRepo;
        this.omeroProjectRepo = omeroProjectRepo;
        this.recipientRepo = recipientRepo;
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
                           String omeroProjectName) {
        checkPrefix(prefix);

        Project project = projectRepo.getByName(projectName);
        Program program = programRepo.getByName(programName);
        CostCode cc = costCodeRepo.getByCode(costCode);
        WorkType type = workTypeRepo.getByName(workTypeName);
        OmeroProject omeroProject;
        if (omeroProjectName==null) {
            omeroProject = null;
        } else {
            omeroProject = omeroProjectRepo.getByName(omeroProjectName);
            if (!omeroProject.isEnabled()) {
                throw new IllegalArgumentException("Omero project "+omeroProject.getName()+" is disabled.");
            }
        }
        ReleaseRecipient workRequester = recipientRepo.getByUsername(workRequesterName);
        if (numBlocks!=null && numBlocks < 0) {
            throw new IllegalArgumentException("Number of blocks cannot be a negative number.");
        }
        if (numSlides!=null && numSlides < 0) {
            throw new IllegalArgumentException("Number of slides cannot be a negative number.");
        }
        if (numOriginalSamples!=null && numOriginalSamples < 0) {
            throw new IllegalArgumentException("Number of original samples cannot be a negative number.");
        }

        String workNumber = workRepo.createNumber(prefix);
        Work work = workRepo.save(new Work(null, workNumber, type, workRequester, project, program, cc, Status.unstarted, numBlocks, numSlides, numOriginalSamples, null, omeroProject));
        workEventService.recordEvent(user, work, WorkEvent.Type.create, null);
        return work;
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
    public Work link(String workNumber, Collection<Operation> operations) {
        Work work = workRepo.getByWorkNumber(workNumber);
        return link(work, operations);
    }

    @Override
    public Work link(Work work, Collection<Operation> operations) {
        if (operations.isEmpty()) {
            return work;
        }
        if (!work.isUsable()) {
            throw new IllegalArgumentException(work.getWorkNumber()+" cannot be used because it is "+ work.getStatus()+".");
        }
        List<Integer> opIds = work.getOperationIds();
        if (!(opIds instanceof ArrayList)) {
            opIds = newArrayList(opIds);
        }
        List<SampleSlotId> ssIds = work.getSampleSlotIds();
        if (!(ssIds instanceof ArrayList)) {
            ssIds = newArrayList(ssIds);
        }
        Set<SampleSlotId> seenSsIds = new HashSet<>(ssIds);
        for (Operation op : operations) {
            opIds.add(op.getId());
            for (Action action : op.getActions()) {
                SampleSlotId ssId = new SampleSlotId(action.getSample().getId(), action.getDestination().getId());
                if (seenSsIds.add(ssId)) {
                    ssIds.add(ssId);
                }
            }
        }

        work.setOperationIds(opIds);
        work.setSampleSlotIds(ssIds);
        return workRepo.save(work);
    }

    @Override
    public Work linkReleases(Work work, List<Release> releases) {
        if (releases.isEmpty()) {
            return work;
        }
        if (!work.isUsable()) {
            throw new IllegalArgumentException("Work "+work.getWorkNumber()+" is not usable because it is "+work.getStatus().name()+".");
        }
        Set<Integer> releaseIds = new LinkedHashSet<>(work.getReleaseIds());
        Set<SampleSlotId> ssids = new LinkedHashSet<>(work.getSampleSlotIds());
        for (Release release : releases) {
            releaseIds.add(release.getId());
            for (Slot slot : release.getLabware().getSlots()) {
                for (Sample sample : slot.getSamples()) {
                    ssids.add(new SampleSlotId(sample.getId(), slot.getId()));
                }
            }
        }
        work.setReleaseIds(new ArrayList<>(releaseIds));
        work.setSampleSlotIds(new ArrayList<>(ssids));
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
                .collect(toList());
        if (!inactiveWorkNumbers.isEmpty()) {
            throw new IllegalArgumentException("Specified work cannot be used because it is not active: "+inactiveWorkNumbers);
        }
        Set<Integer> opIds = operations.stream().map(Operation::getId).collect(toLinkedHashSet());
        Set<SampleSlotId> ssIds = operations.stream()
                .flatMap(op -> op.getActions().stream()
                        .map(a -> new SampleSlotId(a.getSample().getId(), a.getDestination().getId())))
                .collect(toLinkedHashSet());

        for (Work work : works) {
            Set<Integer> workOpIds = new LinkedHashSet<>(work.getOperationIds());
            workOpIds.addAll(opIds);
            work.setOperationIds(new ArrayList<>(workOpIds));

            Set<SampleSlotId> workSsIds = new LinkedHashSet<>(work.getSampleSlotIds());
            workSsIds.addAll(ssIds);
            work.setSampleSlotIds(new ArrayList<>(workSsIds));
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

    @Override
    public Work validateUsableWork(Collection<String> problems, String workNumber) {
        if (workNumber==null) {
            problems.add("Work number is not specified.");
            return null;
        }
        Optional<Work> optWork = workRepo.findByWorkNumber(workNumber);
        if (optWork.isEmpty()) {
            problems.add("Work number not recognised: "+repr(workNumber));
            return null;
        }
        Work work = optWork.get();
        if (!work.isUsable()) {
            problems.add(work.getWorkNumber()+" cannot be used because it is "+work.getStatus()+".");
        }
        return work;
    }

    @Override
    public UCMap<Work> validateUsableWorks(Collection<String> problems, Collection<String> workNumbers) {
        // Check if there are any null workNumbers given
        for (String number : workNumbers) {
            if (number == null) {
                problems.add("Work number is not specified.");
            }
        }
        // Filter out the null numbers for the rest of the checks
        workNumbers.removeAll(Collections.singleton(null));
        // Check there are non-null values before running other checks
        if (workNumbers.isEmpty()) {
            problems.add("No work numbers given.");
            return new UCMap<>(0);
        }
        UCMap<Work> workMap = workRepo.findAllByWorkNumberIn(workNumbers).stream()
                .collect(UCMap.toUCMap(Work::getWorkNumber));

        List<String> missing = workNumbers.stream()
                .filter(s -> workMap.get(s)==null)
                .filter(BasicUtils.distinctUCSerial())
                .collect(toList());

        if (!missing.isEmpty()) {
            problems.add((missing.size()==1 ? "Work number" : "Work numbers") +" not recognised: "+
                    BasicUtils.reprCollection(missing));
        }
        List<String> unusable = new ArrayList<>();
        Set<String> badStates = new LinkedHashSet<>();
        for (Work work : workMap.values()) {
            if (!work.isUsable()) {
                unusable.add(work.getWorkNumber());
                badStates.add(work.getStatus().name());
            }
        }
        if (!unusable.isEmpty()) {
            String problem = String.format("Work %s cannot be used because %s %s: %s",
                    unusable.size()==1 ? "number" : "numbers",
                    unusable.size()==1 ? "it is" : "they are",
                    BasicUtils.commaAndConjunction(badStates, "or"),
                    unusable);
            problems.add(problem);
        }
        return workMap;
    }

    @Override
    public List<WorkWithComment> getWorksWithComments(Collection<Status> statuses) {
        Iterable<Work> works = (statuses==null ? workRepo.findAll() : workRepo.findAllByStatusIn(statuses));
        List<WorkWithComment> wcs = Streamable.of(works).stream()
                .map(WorkWithComment::new)
                .collect(toList());
        List<Integer> pausedOrFailedIds = wcs.stream().map(WorkWithComment::getWork)
                .filter(work -> work.getStatus()==Status.paused || work.getStatus()==Status.failed || work.getStatus()==Status.withdrawn)
                .map(Work::getId)
                .collect(toList());
        if (!pausedOrFailedIds.isEmpty()) {
            Map<Integer, WorkEvent> workEvents = workEventService.loadLatestEvents(pausedOrFailedIds);
            fillInComments(wcs, workEvents);
        }
        return wcs;
    }

    @Override
    public SuggestedWorkResponse suggestWorkForLabwareBarcodes(Collection<String> barcodes) {
        Set<Labware> labware = new HashSet<>(lwRepo.getByBarcodeIn(barcodes));
        Map<String, Integer> barcodeWorkIds = new HashMap<>(labware.size());
        Set<Integer> workIds = new HashSet<>();
        for (Labware lw : labware) {
            Integer workId = workRepo.findLatestActiveWorkIdForLabwareId(lw.getId());
            barcodeWorkIds.put(lw.getBarcode(), workId);
            if (workId!=null) {
                workIds.add(workId);
            }
        }
        Map<Integer, Work> workIdMap = BasicUtils.stream(workRepo.findAllById(workIds))
                .collect(BasicUtils.toMap(Work::getId));
        List<SuggestedWork> suggestedWorks = barcodeWorkIds.entrySet().stream()
                .map(e -> new SuggestedWork(e.getKey(), e.getValue()==null ? null : workIdMap.get(e.getValue()).getWorkNumber()))
                .collect(toList());
        List<Work> works = new ArrayList<>(workIdMap.values());
        return new SuggestedWorkResponse(suggestedWorks, works);
    }

    @Override
    public List<Labware> suggestLabwareForWorkNumber(String workNumber) {
        Work work = workRepo.getByWorkNumber(workNumber);
        List<Integer> possibleLabwareIds = workRepo.findLabwareIdsForWorkIds(List.of(work.getId()));
        if (possibleLabwareIds.isEmpty()) {
            return List.of();
        }
        return lwRepo.findAllByIdIn(possibleLabwareIds).stream()
                .filter(Labware::isUsable)
                .collect(toList());
    }

    public void fillInComments(Collection<WorkWithComment> wcs, Map<Integer, WorkEvent> workEvents) {
        for (WorkWithComment wc : wcs) {
            Work work = wc.getWork();
            WorkEvent event = workEvents.get(work.getId());
            if (event != null && event.getComment()!=null &&
                    (work.getStatus()==Status.paused && event.getType()==WorkEvent.Type.pause
                            || work.getStatus()==Status.failed && event.getType()==WorkEvent.Type.fail|| work.getStatus()==Status.withdrawn && event.getType()==WorkEvent.Type.withdraw)) {
                wc.setComment(event.getComment().getText());
            }
        }
    }
}
