package uk.ac.sanger.sccp.stan.service.work;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Streamable;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.Work.SampleSlotId;
import uk.ac.sanger.sccp.stan.model.Work.Status;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.WorkWithComment;

import java.util.*;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static uk.ac.sanger.sccp.utils.BasicUtils.newArrayList;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

@Service
public class WorkServiceImp implements WorkService {
    private final ProjectRepo projectRepo;
    private final CostCodeRepo costCodeRepo;
    private final WorkTypeRepo workTypeRepo;
    private final WorkRepo workRepo;
    private final WorkEventService workEventService;

    @Autowired
    public WorkServiceImp(ProjectRepo projectRepo, CostCodeRepo costCodeRepo, WorkTypeRepo workTypeRepo, WorkRepo workRepo,
                          WorkEventService workEventService) {
        this.projectRepo = projectRepo;
        this.costCodeRepo = costCodeRepo;
        this.workTypeRepo = workTypeRepo;
        this.workRepo = workRepo;
        this.workEventService = workEventService;
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
    public Work createWork(User user, String prefix, String workTypeName, String projectName, String costCode) {
        checkPrefix(prefix);

        Project project = projectRepo.getByName(projectName);
        CostCode cc = costCodeRepo.getByCode(costCode);
        WorkType type = workTypeRepo.getByName(workTypeName);

        String workNumber = workRepo.createNumber(prefix);
        Work work = workRepo.save(new Work(null, workNumber, type, project, cc, Status.active));
        workEventService.recordEvent(user, work, WorkEvent.Type.create, null);
        return work;
    }

    @Override
    public WorkWithComment updateStatus(User user, String workNumber, Status newStatus, Integer commentId) {
        Work work = workRepo.getByWorkNumber(workNumber);
        WorkEvent event = workEventService.recordStatusChange(user, work, newStatus, commentId);
        work.setStatus(newStatus);
        String commentText = (event.getComment()==null ? null : event.getComment().getText());
        return new WorkWithComment(workRepo.save(work), commentText);
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
        if (work.getStatus()!=Status.active) {
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
    public Work getUsableWork(String workNumber) {
        requireNonNull(workNumber, "Work number is null.");
        Work work = workRepo.getByWorkNumber(workNumber);
        if (!work.isUsable()) {
            throw new IllegalArgumentException(work.getWorkNumber()+" cannot be used because it is "+work.getStatus()+".");
        }
        return work;
    }

    @Override
    public Work validateUsableWork(Collection<String> problems, String workNumber) {
        if (workNumber ==null) {
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
    public List<WorkWithComment> getWorksWithComments(Collection<Status> statuses) {
        Iterable<Work> works = (statuses==null ? workRepo.findAll() : workRepo.findAllByStatusIn(statuses));
        List<WorkWithComment> wcs = Streamable.of(works).stream()
                .map(WorkWithComment::new)
                .collect(toList());
        List<Integer> pausedOrFailedIds = wcs.stream().map(WorkWithComment::getWork)
                .filter(work -> work.getStatus()==Status.paused || work.getStatus()==Status.failed)
                .map(Work::getId)
                .collect(toList());
        if (!pausedOrFailedIds.isEmpty()) {
            Map<Integer, WorkEvent> workEvents = workEventService.loadLatestEvents(pausedOrFailedIds);
            fillInComments(wcs, workEvents);
        }
        return wcs;
    }

    public void fillInComments(Collection<WorkWithComment> wcs, Map<Integer, WorkEvent> workEvents) {
        for (WorkWithComment wc : wcs) {
            Work work = wc.getWork();
            WorkEvent event = workEvents.get(work.getId());
            if (event != null && event.getComment()!=null &&
                    (work.getStatus()==Status.paused && event.getType()==WorkEvent.Type.pause
                            || work.getStatus()==Status.failed && event.getType()==WorkEvent.Type.fail)) {
                wc.setComment(event.getComment().getText());
            }
        }
    }

}
