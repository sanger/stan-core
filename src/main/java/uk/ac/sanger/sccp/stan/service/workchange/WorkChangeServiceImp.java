package uk.ac.sanger.sccp.stan.service.workchange;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.*;
import uk.ac.sanger.sccp.stan.request.OpWorkRequest;
import uk.ac.sanger.sccp.stan.service.ValidationException;
import uk.ac.sanger.sccp.stan.service.work.WorkService;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;

import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.inMap;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * @author dr6
 */
@Service
public class WorkChangeServiceImp implements WorkChangeService {
    Logger log = LoggerFactory.getLogger(WorkChangeServiceImp.class);

    private final WorkChangeValidationService validationService;
    private final WorkService workService;
    private final WorkRepo workRepo;
    private final OperationRepo opRepo;
    private final ReleaseRepo releaseRepo;

    @Autowired
    public WorkChangeServiceImp(WorkChangeValidationService validationService, WorkService workService,
                                WorkRepo workRepo, OperationRepo opRepo, ReleaseRepo releaseRepo) {
        this.validationService = validationService;
        this.workService = workService;
        this.workRepo = workRepo;
        this.opRepo = opRepo;
        this.releaseRepo = releaseRepo;
    }

    @Override
    public List<Operation> perform(OpWorkRequest request) throws ValidationException {
        WorkChangeData data = validationService.validate(request);
        return execute(data.work(), data.ops());
    }

    /**
     * This is called after validation to perform the update
     * @param work the work to link to the operations
     * @param ops the operations
     * @return the operations
     */
    public List<Operation> execute(Work work, List<Operation> ops) {
        clearOutPriorWorks(ops);
        workService.link(work, ops);
        return ops;
    }

    /**
     * Gets the works currently linked to each specified operation
     * @param ops the operations to look up works for
     * @return map from operation id to set of linked works
     */
    public Map<Integer, Set<Work>> loadOpIdWorks(List<Operation> ops) {
        List<Integer> opIds = ops.stream().map(Operation::getId).toList();
        Map<Integer, Set<String>> opIdWorkNumbers = workRepo.findWorkNumbersForOpIds(opIds);
        Set<String> workNumbersToLoad = opIdWorkNumbers.values().stream()
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .collect(toSet());
        if (workNumbersToLoad.isEmpty()) {
            return Map.of();
        }
        UCMap<Work> workMap = workRepo.getMapByWorkNumberIn(workNumbersToLoad);
        Map<Integer, Set<Work>> opIdWorks = new HashMap<>(ops.size());
        for (Operation op : ops) {
            Set<String> wns = opIdWorkNumbers.get(op.getId());
            Set<Work> workSet;
            if (nullOrEmpty(wns)) {
                workSet = Set.of();
            } else {
                workSet = wns.stream().map(workMap::get).collect(toSet());
            }
            opIdWorks.put(op.getId(), workSet);
        }
        return opIdWorks;
    }

    /**
     * Finds slot/samples that a work should still be linked to without the indicated unlinked operations
     * @param works the works to check
     * @param excludedOpIds the operation ids to exclude
     * @return map from work id to relevant slot/sample ids
     */
    public Map<Integer, Set<SlotIdSampleId>> workExtantSlotSampleIds(Collection<Work> works,
                                                                     Set<Integer> excludedOpIds) {
        // All the operation ids that the works are still linked to
        Set<Integer> otherOpIds = works.stream()
                .filter(work -> !nullOrEmpty(work.getOperationIds()))
                .flatMap(work -> work.getOperationIds().stream())
                .filter(opId -> !excludedOpIds.contains(opId))
                .collect(toSet());
        // All the release ids that the works are linked to
        Set<Integer> releaseIds = works.stream()
                .filter(work -> !nullOrEmpty(work.getReleaseIds()))
                .flatMap(work -> work.getReleaseIds().stream())
                .collect(toSet());
        // Find slotSampleIds for each operation (that is still linked to a work)
        Map<Integer, Set<SlotIdSampleId>> otherOpSlotSampleIds = opRepo.findOpSlotSampleIds(otherOpIds);
        Map<Integer, Set<SlotIdSampleId>> releaseSlotSampleIds = releaseRepo.findReleaseSlotSampleIds(releaseIds);
        Map<Integer, Set<SlotIdSampleId>> workExtantSlotSampleIds = new HashMap<>(works.size());
        for (Work work : works) {
            if (nullOrEmpty(work.getOperationIds()) && nullOrEmpty(work.getReleaseIds())) {
                workExtantSlotSampleIds.put(work.getId(), Set.of());
            } else {
                Set<SlotIdSampleId> wssids = new HashSet<>();
                if (!nullOrEmpty(work.getOperationIds())) {
                    for (Integer opId : work.getOperationIds()) {
                        Set<SlotIdSampleId> ssids = otherOpSlotSampleIds.get(opId);
                        if (!nullOrEmpty(ssids)) {
                            wssids.addAll(ssids);
                        }
                    }
                }
                if (!nullOrEmpty(work.getReleaseIds())) {
                    for (Integer releaseId : work.getReleaseIds()) {
                        Set<SlotIdSampleId> rssids = releaseSlotSampleIds.get(releaseId);
                        if (!nullOrEmpty(rssids)) {
                            wssids.addAll(rssids);
                        }
                    }
                }
                workExtantSlotSampleIds.put(work.getId(), wssids);
            }
        }

        return workExtantSlotSampleIds;
    }

    /** Gets the slot/samples that are targeted by a given operation */
    public Set<SlotIdSampleId> getOpSlotSampleIds(Operation op) {
        return op.getActions().stream()
                .map(ac -> new SlotIdSampleId(ac.getDestination(), ac.getSample()))
                .collect(toSet());
    }

    /**
     * Finds the Work.SampleSlotIds that should be removed from each work
     * @param ops the operations being unlinked
     * @param workIdMap map to get works from work id
     * @param opIdWorks map of operation id to linked works
     * @return map from work id to the SampleSlotIds that should be removed from that work
     */
    @NotNull
    public Map<Integer, Set<Work.SampleSlotId>> findSampleSlotIdsToRemove(List<Operation> ops, Map<Integer, Work> workIdMap,
                                                                          Map<Integer, Set<Work>> opIdWorks) {
        Map<Integer, Set<SlotIdSampleId>> extantWorkSsIds = workExtantSlotSampleIds(workIdMap.values(), opIdWorks.keySet());
        Map<Integer, Set<Work.SampleSlotId>> workSsidsToRemove = new HashMap<>();

        // Note that SlotIdSampleId and Work.SampleSlotId are distinct types with similar structures
        for (Operation op : ops) {
            if (!nullOrEmpty(opIdWorks.get(op.getId()))) {
                Set<SlotIdSampleId> opSlotSampleIds = getOpSlotSampleIds(op);
                for (Work work : opIdWorks.get(op.getId())) {
                    Set<SlotIdSampleId> workSsids = extantWorkSsIds.get(work.getId());
                    var toRemove = opSlotSampleIds.stream()
                            .filter(ssid -> !workSsids.contains(ssid))
                            .map(ss -> new Work.SampleSlotId(ss.getSampleId(), ss.getSlotId()))
                            .collect(toSet());
                    workSsidsToRemove.computeIfAbsent(work.getId(), k -> new HashSet<>()).addAll(toRemove);
                }
            }
        }
        return workSsidsToRemove;
    }

    /**
     * For each operation:
     *  <ul>
     *      <li>Get its slotSampleIds <tt>X</tt></li>
     *      <li>For each of its previously linked works:<ul>
     *          <li>Find the slotSampleIds it's linked to via other ops/releases <tt>Y</tt></li>
     *          <li>We will want to remove from the work any elements of <tt>X</tt>
     *              that are not in <tt>Y</tt></li>
     *      </ul></li>
     *  </ul>
     * @param ops operations having their works removed
     */
    public void clearOutPriorWorks(List<Operation> ops) {
        Map<Integer, Set<Work>> opIdWorks = loadOpIdWorks(ops);
        if (opIdWorks.isEmpty()) {
            return; // nothing to do
        }
        // A map from work id to work (the works having things removed)
        Map<Integer, Work> workIdMap = opIdWorks.values().stream()
                .flatMap(Collection::stream)
                .distinct()
                .collect(inMap(Work::getId));

        Map<Integer, Set<Work.SampleSlotId>> workSsidsToRemove = findSampleSlotIdsToRemove(ops, workIdMap, opIdWorks);

        log.info("WorkChange: Removing sample/slot ids from work ids, where present: {}", workSsidsToRemove);

        workSsidsToRemove.forEach((workId, toRemove) -> {
            if (!nullOrEmpty(toRemove)) {
                workIdMap.get(workId).getSampleSlotIds().removeAll(toRemove);
            }
        });

        log.info("WorkChange: Removing operations {} from work ids {}", opIdWorks.keySet(), workIdMap.keySet());

        // remove the affected op ids from the works
        for (Work work : workIdMap.values()) {
            work.getOperationIds().removeAll(opIdWorks.keySet());
        }
        workRepo.saveAll(workIdMap.values());
    }
}
