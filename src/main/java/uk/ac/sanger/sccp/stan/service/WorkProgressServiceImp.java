package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.Work.Status;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.WorkProgress;
import uk.ac.sanger.sccp.stan.request.WorkProgress.WorkProgressTimestamp;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;

/**
 * @author dr6
 */
@Service
public class WorkProgressServiceImp implements WorkProgressService {
    private final WorkRepo workRepo;
    private final WorkTypeRepo workTypeRepo;
    private final OperationRepo opRepo;
    // Consider for the future moving this set to a config class and injecting it
    private final Set<String> includedOpTypes = Set.of("section", "stain", "extract", "visium cdna");

    @Autowired
    public WorkProgressServiceImp(WorkRepo workRepo, WorkTypeRepo workTypeRepo, OperationRepo opRepo) {
        this.workRepo = workRepo;
        this.workTypeRepo = workTypeRepo;
        this.opRepo = opRepo;
    }

    @Override
    public List<WorkProgress> getProgress(String workNumber, List<String> workTypeNames, List<Status> statuses) {
        Work singleWork = (workNumber==null ? null : workRepo.getByWorkNumber(workNumber));
        List<WorkType> workTypes;
        if (workTypeNames==null) {
            workTypes = null;
        } else if (workTypeNames.isEmpty()) {
            return List.of();
        } else {
            workTypes = workTypeRepo.getAllByNameIn(workTypeNames);
        }
        if (workTypes!=null && workTypes.isEmpty() || statuses!=null && statuses.isEmpty()) {
            return List.of();
        }
        if (singleWork!=null) {
            if (workTypes!=null && !workTypes.contains(singleWork.getWorkType())) {
                return List.of();
            }
            if (statuses!=null && !statuses.contains(singleWork.getStatus())) {
                return List.of();
            }
            return List.of(getProgressForWork(singleWork));
        }
        if (workTypes!=null) {
            List<Work> works = workRepo.findAllByWorkTypeIn(workTypes);
            Stream<Work> workStream = works.stream();
            if (statuses!=null && !statuses.isEmpty()) {
                workStream = workStream.filter(work -> statuses.contains(work.getStatus()));
            }
            return workStream.map(this::getProgressForWork).collect(toList());
        }
        Iterable<Work> works;
        if (statuses!=null) {
            works = workRepo.findAllByStatusIn(statuses);
        } else {
            works = workRepo.findAll();
        }
        return StreamSupport.stream(works.spliterator(), false)
                .map(this::getProgressForWork)
                .collect(toList());
    }

    public WorkProgress getProgressForWork(Work work) {
        var opTimes = loadOpTimes(work);
        List<WorkProgressTimestamp> workTimes = opTimes.entrySet().stream()
                .map(e -> new WorkProgressTimestamp(e.getKey(), e.getValue()))
                .collect(toList());
        return new WorkProgress(work, workTimes);
    }

    public Map<String, LocalDateTime> loadOpTimes(Work work) {
        Iterable<Operation> ops = opRepo.findAllById(work.getOperationIds());
        Map<String, LocalDateTime> opTimes = new HashMap<>(4);
        Map<Integer, Boolean> opTypeIncluded = new HashMap<>();
        for (Operation op : ops) {
            OperationType opType = op.getOperationType();
            Boolean included = opTypeIncluded.get(opType.getId());
            //noinspection Java8MapApi
            if (included==null) {
                included = includedOpTypes.contains(opType.getName().toLowerCase());
                opTypeIncluded.put(opType.getId(), included);
            }
            if (included) {
                String key = opType.getName();
                LocalDateTime thisTime = op.getPerformed();
                LocalDateTime savedTime = opTimes.get(key);
                if (savedTime==null || savedTime.isBefore(thisTime)) {
                    opTimes.put(key, thisTime);
                }
            }
        }
        return opTimes;
    }
}
