package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.Work.Status;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.WorkProgress;
import uk.ac.sanger.sccp.stan.request.WorkProgress.WorkProgressTimestamp;
import uk.ac.sanger.sccp.utils.EntityNameFilter;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * @author dr6
 */
@Service
public class WorkProgressServiceImp implements WorkProgressService {
    private final WorkRepo workRepo;
    private final WorkTypeRepo workTypeRepo;
    private final OperationRepo opRepo;
    private final LabwareRepo lwRepo;
    // Consider for the future moving these sets to a config class and injecting them
    private final Set<String> includedOpTypes = Set.of("section", "stain", "extract", "visium cdna", "image",
            "rin analysis", "dv200 analysis");
    private final Set<String> specialStainTypes = Set.of("rnascope", "ihc");
    private final Set<String> specialLabwareTypes = Set.of("visium to", "visium lp");

    @Autowired
    public WorkProgressServiceImp(WorkRepo workRepo, WorkTypeRepo workTypeRepo, OperationRepo opRepo,
                                  LabwareRepo lwRepo) {
        this.workRepo = workRepo;
        this.workTypeRepo = workTypeRepo;
        this.opRepo = opRepo;
        this.lwRepo = lwRepo;
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
        EntityNameFilter<OperationType> opTypeFilter = new EntityNameFilter<>(includedOpTypes);
        EntityNameFilter<StainType> stainTypeFilter = new EntityNameFilter<>(specialStainTypes);
        EntityNameFilter<LabwareType> labwareTypeFilter = new EntityNameFilter<>(specialLabwareTypes);
        final Map<Integer, LabwareType> labwareIdToType = new HashMap<>();
        if (singleWork!=null) {
            if (workTypes!=null && !workTypes.contains(singleWork.getWorkType())) {
                return List.of();
            }
            if (statuses!=null && !statuses.contains(singleWork.getStatus())) {
                return List.of();
            }
            return List.of(getProgressForWork(singleWork, opTypeFilter, stainTypeFilter, labwareTypeFilter, labwareIdToType));
        }
        if (workTypes!=null) {
            List<Work> works = workRepo.findAllByWorkTypeIn(workTypes);
            Stream<Work> workStream = works.stream();
            if (statuses!=null) {
                workStream = workStream.filter(work -> statuses.contains(work.getStatus()));
            }
            return workStream
                    .map(work -> getProgressForWork(work, opTypeFilter, stainTypeFilter, labwareTypeFilter, labwareIdToType))
                    .collect(toList());
        }
        Iterable<Work> works;
        if (statuses!=null) {
            works = workRepo.findAllByStatusIn(statuses);
        } else {
            works = workRepo.findAll();
        }
        return StreamSupport.stream(works.spliterator(), false)
                .map(work -> getProgressForWork(work, opTypeFilter, stainTypeFilter, labwareTypeFilter, labwareIdToType))
                .collect(toList());
    }

    /**
     * Gets the work progress for a particular work
     * @param work the work
     * @param includeOpType predicate to filter operation types
     * @param specialStainType predicate to filter stain types for the special stain time
     * @param specialLabwareType predicate to filter labware types where they are mentioned specifically
     * @param labwareIdToType a cache of labware id to its labware type
     * @return the work progress for the given work
     */
    public WorkProgress getProgressForWork(Work work,
                                           Predicate<OperationType> includeOpType,
                                           Predicate<StainType> specialStainType,
                                           Predicate<LabwareType> specialLabwareType,
                                           Map<Integer, LabwareType> labwareIdToType) {
        Map<String, LocalDateTime> opTimes = loadOpTimes(work, includeOpType, specialStainType, specialLabwareType,
                labwareIdToType);
        List<WorkProgressTimestamp> workTimes = opTimes.entrySet().stream()
                .map(e -> new WorkProgressTimestamp(e.getKey(), e.getValue()))
                .collect(toList());
        return new WorkProgress(work, workTimes);
    }

    /**
     * Gets a map of different events to latest matching operation timestamps
     * @param work the work for the events
     * @param includeOpType predicate to filter op types
     * @param specialStainType predicate to filter stain types for the special stain time
     * @param specialLabwareType predicate to filter labware types where they are mentioned specifically
     * @param labwareIdToType a cache of labware id to its labware type
     * @return a map from event labels to the latest matching operation timestamp
     */
    public Map<String, LocalDateTime> loadOpTimes(Work work,
                                                  Predicate<OperationType> includeOpType,
                                                  Predicate<StainType> specialStainType,
                                                  Predicate<LabwareType> specialLabwareType,
                                                  Map<Integer, LabwareType> labwareIdToType) {
        Iterable<Operation> ops = opRepo.findAllById(work.getOperationIds());
        Map<String, LocalDateTime> opTimes = new HashMap<>();

        for (Operation op : ops) {
            OperationType opType = op.getOperationType();
            if (!includeOpType.test(opType)) {
                continue;
            }
            String key = opType.getName();
            if (opType.has(OperationTypeFlag.STAIN)) {
                if (specialStainType.test(op.getStainType())) {
                    addTime(opTimes, "RNAscope/IHC stain", op.getPerformed());
                }
                Set<LabwareType> labwareTypes = opLabwareTypes(op, labwareIdToType);
                labwareTypes.stream().filter(specialLabwareType)
                        .forEach(lt -> addTime(opTimes, "Stain "+lt.getName(), op.getPerformed()));
            }
            if (opType.has(OperationTypeFlag.ANALYSIS)) {
                key = "Analysis"; // RIN/DV200 analysis
            }
            addTime(opTimes, key, op.getPerformed());
        }
        return opTimes;
    }

    /**
     * Gets all labware types of destinations of the given operation
     * @param op the operation
     * @param lwIdToType a cache of labware id to its labware type to avoid repeated lookups
     * @return the distinct labware types of destinations in the given operation
     */
    public Set<LabwareType> opLabwareTypes(Operation op, final Map<Integer, LabwareType> lwIdToType) {
        return op.getActions().stream()
                .map(a -> a.getDestination().getLabwareId())
                .distinct()
                .map(id -> getLabwareType(id, lwIdToType))
                .collect(toSet());
    }

    /**
     * Gets the labware type for the given labware id
     * @param labwareId the labware id
     * @param lwIdToType a cache to avoid repeated lookups
     * @return the labware type of the given labware id
     */
    public LabwareType getLabwareType(Integer labwareId, Map<Integer, LabwareType> lwIdToType) {
        LabwareType lt = lwIdToType.get(labwareId);
        if (lt==null) {
            lt = lwRepo.getById(labwareId).getLabwareType();
            lwIdToType.put(labwareId, lt);
        }
        return lt;
    }

    /**
     * Incorporates a given timestamp in the given map.
     * If the key is not already in the map, or if the new timestamp is later than the saved timestamp,
     * it is added to the map.
     * @param opTimes map to the latest matching timestamp for the given key
     * @param key the key indicating the meaning of the time
     * @param thisTime the new time
     * @param <K> the type of key used in the map
     */
    public <K> void addTime(Map<K, LocalDateTime> opTimes, K key, LocalDateTime thisTime) {
        LocalDateTime savedTime = opTimes.get(key);
        if (savedTime==null || savedTime.isBefore(thisTime)) {
            opTimes.put(key, thisTime);
        }
    }
}
