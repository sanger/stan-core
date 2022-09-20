package uk.ac.sanger.sccp.stan.service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.Work.Status;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.WorkProgress;
import uk.ac.sanger.sccp.stan.request.WorkProgress.WorkProgressTimestamp;
import uk.ac.sanger.sccp.stan.service.work.WorkEventService;
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
    private final ReleaseRepo releaseRepo;
    private final StainTypeRepo stainTypeRepo;
    private final WorkEventService workEventService;
    // Consider for the future moving these sets to a config class and injecting them
    private final Set<String> includedOpTypes = Set.of("section", "stain", "extract", "visium cdna", "image",
            "rin analysis", "dv200 analysis");
    private final Set<String> specialStainTypes = Set.of("rnascope", "ihc");
    private final Set<String> specialLabwareTypes = Set.of("visium to", "visium lp");
    private final Map<String,Set<String>> labwareTypeToStainMap = Map.of("visium adh", Set.of("h&e"));
    private final Set<String> releaseLabwareTypes = Set.of("96 well plate");

    @Autowired
    public WorkProgressServiceImp(WorkRepo workRepo, WorkTypeRepo workTypeRepo, OperationRepo opRepo,
                                  LabwareRepo lwRepo, ReleaseRepo releaseRepo, StainTypeRepo stainTypeRepo, WorkEventService workEventService) {
        this.workRepo = workRepo;
        this.workTypeRepo = workTypeRepo;
        this.opRepo = opRepo;
        this.lwRepo = lwRepo;
        this.releaseRepo = releaseRepo;
        this.stainTypeRepo = stainTypeRepo;
        this.workEventService = workEventService;
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
        EntityNameFilter<LabwareType> releaseLabwareTypeFilter = new EntityNameFilter<>(releaseLabwareTypes);

        final Map<Integer, Labware> labwareIdToLabware = new HashMap<>();

        if (singleWork!=null) {
            if (workTypes!=null && !workTypes.contains(singleWork.getWorkType())) {
                return List.of();
            }
            if (statuses!=null && !statuses.contains(singleWork.getStatus())) {
                return List.of();
            }
            return List.of(getProgressForWork(singleWork, opTypeFilter, stainTypeFilter, labwareTypeFilter,
                    releaseLabwareTypeFilter, labwareIdToLabware, labwareTypeToStainMap));
        }
        if (workTypes!=null) {
            List<Work> works = workRepo.findAllByWorkTypeIn(workTypes);
            Stream<Work> workStream = works.stream();
            if (statuses!=null) {
                workStream = workStream.filter(work -> statuses.contains(work.getStatus()));
            }
            return workStream
                    .map(work -> getProgressForWork(work, opTypeFilter, stainTypeFilter, labwareTypeFilter,
                            releaseLabwareTypeFilter, labwareIdToLabware, labwareTypeToStainMap))
                    .collect(toList());
        }
        Iterable<Work> works;
        if (statuses!=null) {
            works = workRepo.findAllByStatusIn(statuses);
        } else {
            works = workRepo.findAll();
        }
        return StreamSupport.stream(works.spliterator(), false)
                .map(work -> getProgressForWork(work, opTypeFilter, stainTypeFilter, labwareTypeFilter,
                        releaseLabwareTypeFilter, labwareIdToLabware, labwareTypeToStainMap))
                .collect(toList());
    }

    /**
     * Gets the work progress for a particular work
     * @param work the work
     * @param includeOpType predicate to filter operation types
     * @param specialStainType predicate to filter stain types for the special stain time
     * @param specialLabwareType predicate to filter labware types where they are mentioned specifically
     * @param releaseLabwareType predicate to filter release labware types where they are mentioned specifically
     * @param labwareIdToLabware a cache of labware id to its labware
     * @param labwareTypeToStainMap a map of labware id to stain types to record
     * @return the work progress for the given work
     */
    public WorkProgress getProgressForWork(Work work,
                                           Predicate<OperationType> includeOpType,
                                           Predicate<StainType> specialStainType,
                                           Predicate<LabwareType> specialLabwareType,
                                           Predicate<LabwareType> releaseLabwareType,
                                           Map<Integer, Labware> labwareIdToLabware,
                                           Map<String, Set<String>> labwareTypeToStainMap) {
        Map<String, LocalDateTime> opTimes = loadOpTimes(work, includeOpType, specialStainType, specialLabwareType,
                releaseLabwareType, labwareIdToLabware, labwareTypeToStainMap);
        List<WorkProgressTimestamp> workTimes = opTimes.entrySet().stream()
                .map(e -> new WorkProgressTimestamp(e.getKey(), e.getValue()))
                .collect(toList());
        String mostRecentOperation = getMostRecentOperation(workTimes);
        String workComment = getWorkComment(work);
        return new WorkProgress(work, workTimes, mostRecentOperation, workComment);
    }

    /**
     * Gets a map of different events to latest matching operation timestamps
     * @param work the work for the events
     * @param includeOpType predicate to filter op types
     * @param specialStainType predicate to filter stain types for the special stain time
     * @param specialLabwareType predicate to filter labware types where they are mentioned specifically
     * @param releaseLabwareType predicate to filter release labware types where they are mentioned specifically
     * @param labwareIdToLabware a cache of labware id to its labware
     * @param labwareTypeToStainMap a map of labware id to stain types to record
     * @return a map from event labels to the latest matching operation timestamp
     */
    public Map<String, LocalDateTime> loadOpTimes(Work work,
                                                  Predicate<OperationType> includeOpType,
                                                  Predicate<StainType> specialStainType,
                                                  Predicate<LabwareType> specialLabwareType,
                                                  Predicate<LabwareType> releaseLabwareType,
                                                  Map<Integer, Labware> labwareIdToLabware,
                                                  Map<String, Set<String>> labwareTypeToStainMap) {
        final var opIds = work.getOperationIds();
        Iterable<Operation> ops = opRepo.findAllById(opIds);
        Map<String, LocalDateTime> opTimes = new HashMap<>();
        var opStainTypes = stainTypeRepo.loadOperationStainTypes(opIds);

        for (Operation op : ops) {
            OperationType opType = op.getOperationType();
            if (!includeOpType.test(opType)) {
                continue;
            }
            String key = opType.getName();
            if (opType.has(OperationTypeFlag.STAIN)) {
                Set<LabwareType> labwareTypes = opLabwares(op, labwareIdToLabware).stream()
                        .map(Labware::getLabwareType)
                        .collect(toSet());
                labwareTypes.stream().filter(specialLabwareType)
                        .forEach(lt -> addTime(opTimes, "Stain "+lt.getName(), op.getPerformed()));

                for (LabwareType lt : labwareTypes) {
                    var soughtStainTypes = labwareTypeToStainMap.get(lt.getName().toLowerCase());
                    var thisOpStainTypes = opStainTypes.get(op.getId());

                    if (soughtStainTypes!=null && thisOpStainTypes!=null) {
                        for (StainType st : thisOpStainTypes) {
                            if (soughtStainTypes.contains(st.getName().toLowerCase())) {
                                addTime(opTimes, lt.getName()+" "+st.getName() + " stain", op.getPerformed());
                            }
                        }
                    }
                }
                List<StainType> sts = opStainTypes.get(op.getId());
                if (sts!=null && sts.stream().anyMatch(specialStainType)) {
                    addTime(opTimes, "RNAscope/IHC stain", op.getPerformed());
                }
            }
            if (opType.has(OperationTypeFlag.ANALYSIS)) {
                key = "Analysis"; // RIN/DV200 analysis
            }
            addTime(opTimes, key, op.getPerformed());
        }

        loadReleases(opTimes, ops, releaseLabwareType, labwareIdToLabware);
        return opTimes;
    }

    /**
     * Calculates the last operation that occurred
     * @param workProgressTimestamps the list of workProgressTimestamps (work operation times)
     * @return the name of the latest operation in the list
     */
    public String getMostRecentOperation(List<WorkProgressTimestamp> workProgressTimestamps) {
        if (workProgressTimestamps != null && !workProgressTimestamps.isEmpty()) {
            WorkProgressTimestamp maxwpt = workProgressTimestamps.stream().max(Comparator.comparing(WorkProgressTimestamp::getTimestamp)).get();
            return maxwpt.getType();
        }
        return null;
    }

    /**
     * Loads releases dates of (filtered) destination labware from any of the given operations.
     * @param opTimes the map of event labels to times
     * @param ops the operations to get the labware from
     * @param releaseLabwareFilter the filter specifying whether each labware should be included
     * @param labwareIdCache a cache of labware from its id
     */
    public void loadReleases(Map<String, LocalDateTime> opTimes, Iterable<Operation> ops,
                             Predicate<LabwareType> releaseLabwareFilter,
                             Map<Integer, Labware> labwareIdCache) {
        Set<Integer> lwIdsToCheckForReleases = StreamSupport.stream(ops.spliterator(), false)
                .flatMap(op -> op.getActions().stream()
                        .map(ac -> ac.getDestination().getLabwareId()))
                .filter(lwid -> releaseLabwareFilter.test(getLabware(lwid, labwareIdCache).getLabwareType()))
                .collect(toSet());

        for (Release release : releaseRepo.findAllByLabwareIdIn(lwIdsToCheckForReleases)) {
            addTime(opTimes, "Release " + release.getLabware().getLabwareType().getName(), release.getReleased());
        }
    }

    /**
     * Gets all labware of destinations of the given operation
     * @param op the operation
     * @param lwIdToLabware a cache of labware id to its labwares to avoid repeated lookups
     * @return the distinct labware types of destinations in the given operation
     */
    public Set<Labware> opLabwares(Operation op, final Map<Integer, Labware> lwIdToLabware) {
        return op.getActions().stream()
                .map(a -> a.getDestination().getLabwareId())
                .distinct()
                .map(id -> getLabware(id, lwIdToLabware))
                .collect(toSet());
    }

    /**
     * Gets the labware type for the given labware id
     * @param labwareId the labware id
     * @param lwIdToLabware a cache to avoid repeated lookups
     * @return the labware type of the given labware id
     */
    public Labware getLabware(Integer labwareId, Map<Integer, Labware> lwIdToLabware) {
        Labware lw = lwIdToLabware.get(labwareId);
        if (lw==null) {
            lw = lwRepo.getById(labwareId);
            lwIdToLabware.put(labwareId, lw);
        }
        return lw;
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

    public String getWorkComment(Work work) {
        if (work.getStatus()==Status.paused || work.getStatus()==Status.failed || work.getStatus()==Status.withdrawn) {
            Map<Integer, WorkEvent> workEvents = workEventService.loadLatestEvents(List.of(work.getId()));
            WorkEvent event = workEvents.get(work.getId());
            if (event != null && event.getComment()!=null &&
                    (work.getStatus()==Status.paused && event.getType()==WorkEvent.Type.pause
                            || work.getStatus()==Status.failed && event.getType()==WorkEvent.Type.fail|| work.getStatus()==Status.withdrawn && event.getType()==WorkEvent.Type.withdraw)) {
                return event.getComment().getText();
            }
        }
        return null;
    }
}
