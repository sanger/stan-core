package uk.ac.sanger.sccp.stan.service.flag;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.model.LabwareFlag.Priority;
import uk.ac.sanger.sccp.stan.repo.LabwareFlagRepo;
import uk.ac.sanger.sccp.stan.repo.OperationRepo;
import uk.ac.sanger.sccp.stan.request.FlagDetail;
import uk.ac.sanger.sccp.stan.request.FlagDetail.FlagSummary;
import uk.ac.sanger.sccp.stan.request.LabwareFlagged;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.Ancestry;
import uk.ac.sanger.sccp.stan.service.releasefile.Ancestoriser.SlotSample;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * @author dr6
 */
@Service
public class FlagLookupServiceImp implements FlagLookupService {
    static final Comparator<Priority> PRIORITY_COMPARATOR = Comparator.nullsFirst(Comparator.naturalOrder());

    private final Ancestoriser ancestoriser;
    private final LabwareFlagRepo flagRepo;
    private final OperationRepo opRepo;

    @Autowired
    public FlagLookupServiceImp(Ancestoriser ancestoriser, LabwareFlagRepo flagRepo, OperationRepo opRepo) {
        this.ancestoriser = ancestoriser;
        this.flagRepo = flagRepo;
        this.opRepo = opRepo;
    }

    @Override
    @NotNull
    public UCMap<List<LabwareFlag>> lookUp(Collection<Labware> labware) {
        if (labware.isEmpty()) {
            return new UCMap<>(0);
        }

        Ancestry ancestry = loadAncestry(labware);
        Map<SlotSample, List<LabwareFlag>> ssFlags = loadDirectFlags(ancestry.keySet());
        if (ssFlags.isEmpty()) {
            return new UCMap<>(0);
        }

        UCMap<List<LabwareFlag>> lwFlags = new UCMap<>(labware.size());
        for (Labware lw : labware) {
            List<LabwareFlag> flags = flagsForLabware(ancestry, lw, ssFlags);
            lwFlags.put(lw.getBarcode(), flags);
        }
        return lwFlags;
    }

    /**
     * Loads the ancestry for the given labware
     * @param labware the labware to look up ancestry for
     * @return the ancestry of the given labware
     */
    @NotNull
    Ancestry loadAncestry(Collection<Labware> labware) {
        List<SlotSample> slotSamples = labware.stream()
                .flatMap(SlotSample::stream)
                .collect(toList());
        return ancestoriser.findAncestry(slotSamples);
    }

    /**
     * Looks up flags directly recorded on the given slotsamples.
     * @param slotSamples the slots and samples to look up flags for
     * @return a map of slot samples to applicable flags
     */
    @NotNull
    Map<SlotSample, List<LabwareFlag>> loadDirectFlags(Collection<SlotSample> slotSamples) {
        Set<Integer> labwareIds = slotSamples.stream().map(ss -> ss.slot().getLabwareId()).collect(toSet());
        List<LabwareFlag> flags = flagRepo.findAllByLabwareIdIn(labwareIds);
        if (flags.isEmpty()) {
            return Map.of();
        }
        Set<Integer> opIds = new HashSet<>();
        Map<OpIdLwId, List<LabwareFlag>> opLwFlagMap = new HashMap<>();
        for (LabwareFlag flag : flags) {
            opIds.add(flag.getOperationId());
            opLwFlagMap.computeIfAbsent(new OpIdLwId(flag.getOperationId(), flag.getLabware().getId()), k -> new ArrayList<>())
                    .add(flag);
        }
        return makeSsFlagMap(opIds, opLwFlagMap);
    }

    /**
     * Loads the ops for the indicated flags and compiles a map from slot samples to their direct flags
     * @param opIds all the op ids in the {@code opLwFlagMap}
     * @param opLwFlagMap map of op id and labware id to the flags directly on that labware recorded in that op
     * @return a map from slot sample to the flags directly on that slot sample
     */
    @NotNull
    Map<SlotSample, List<LabwareFlag>> makeSsFlagMap(Set<Integer> opIds, Map<OpIdLwId, List<LabwareFlag>> opLwFlagMap) {
        Map<SlotSample, List<LabwareFlag>> ssFlags = new HashMap<>();
        for (Operation op : opRepo.findAllById(opIds)) {
            for (Action ac : op.getActions()) {
                List<LabwareFlag> lwFlags = opLwFlagMap.get(new OpIdLwId(op.getId(), ac.getDestination().getLabwareId()));
                if (nullOrEmpty(lwFlags)) {
                    continue;
                }
                SlotSample ss = new SlotSample(ac.getDestination(), ac.getSample());
                var flagsForSs = ssFlags.get(ss);
                if (flagsForSs==null) {
                    ssFlags.put(ss, new ArrayList<>(lwFlags));
                } else {
                    flagsForSs.addAll(lwFlags);
                }
            }
        }
        return ssFlags;
    }

    /**
     * Gets the flags relevant to the labware using the ancestry.
     * @param ancestry the ancestry of the labware
     * @param lw the labware to get flags for
     * @param ssFlags a map of flags on slot-samples that include the labware's ancestors
     * @return the flags recorded on ancestors of the given labware
     */
    @NotNull
    List<LabwareFlag> flagsForLabware(Ancestry ancestry, Labware lw, Map<SlotSample, List<LabwareFlag>> ssFlags) {
        Set<LabwareFlag> flags = new LinkedHashSet<>();
        SlotSample.stream(lw).forEach(lwSs -> {
            for (SlotSample ss : ancestry.ancestors(lwSs)) {
                final List<LabwareFlag> ancestorFlags = ssFlags.get(ss);
                if (!nullOrEmpty(ancestorFlags)) {
                    flags.addAll(ancestorFlags);
                }
            }
        });
        if (flags.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(flags);
    }

    @Override
    public List<FlagDetail> toDetails(UCMap<List<LabwareFlag>> flagMap) {
        return flagMap.entrySet().stream()
                .map(e -> toDetail(e.getKey(), e.getValue()))
                .collect(toList());
    }

    Map<Integer, Priority> opIdPriority(Collection<LabwareFlag> flags) {
        Map<Integer, Priority> map = new HashMap<>(flags.size());
        for (LabwareFlag lf : flags) {
            Integer opId = lf.getOperationId();
            if (PRIORITY_COMPARATOR.compare(map.get(opId), lf.getPriority()) < 0) {
                map.put(opId, lf.getPriority());
            }
        }
        return map;
    }

    Priority labwareFlagPriority(Labware lw) {
        requireNonNull(lw, "Labware is null");
        Set<SlotSample> slotSamples = SlotSample.stream(lw).collect(toSet());
        Ancestry ancestry = ancestoriser.findAncestry(slotSamples);
        Set<SlotSample> ancestorSS = ancestry.keySet();
        Set<Integer> labwareIds = ancestorSS.stream().map(ss -> ss.slot().getLabwareId()).collect(toSet());
        List<LabwareFlag> flags = flagRepo.findAllByLabwareIdIn(labwareIds);
        if (flags.isEmpty()) {
            return null;
        }
        Map<Integer, Priority> opIdPriority = opIdPriority(flags);
        Iterable<Operation> ops = opRepo.findAllById(opIdPriority.keySet());
        Priority priority = null;
        for (Operation op : ops) {
            Priority opPriority = opIdPriority.get(op.getId());
            if (PRIORITY_COMPARATOR.compare(priority, opPriority) < 0) {
                for (Action ac : op.getActions()) {
                    if (ancestorSS.contains(new SlotSample(ac.getDestination(), ac.getSample()))) {
                        priority = opPriority;
                    }
                }
            }
        }
        return priority;
    }

    @Override
    public List<LabwareFlagged> getLabwareFlagged(Collection<Labware> labware) {
        requireNonNull(labware);
        if (labware.isEmpty()) {
            return List.of();
        }
        Set<SlotSample> slotSamples = labware.stream()
                .flatMap(SlotSample::stream)
                .collect(toSet());
        Ancestry ancestry = ancestoriser.findAncestry(slotSamples);
        Set<SlotSample> ancestorSs = ancestry.keySet();
        Set<Integer> labwareIds = ancestorSs.stream().map(ss -> ss.slot().getLabwareId()).collect(toSet());
        List<LabwareFlag> flags = flagRepo.findAllByLabwareIdIn(labwareIds);
        if (flags.isEmpty()) {
            return labware.stream().map(lw -> new LabwareFlagged(lw, null)).toList();
        }
        Map<Integer, Priority> opIdPriority = opIdPriority(flags);
        Set<Integer> opIds = flags.stream().map(LabwareFlag::getOperationId).collect(toSet());
        Iterable<Operation> ops = opRepo.findAllById(opIds);
        Map<SlotSample, Priority> ssPriorities = new HashMap<>();
        for (Operation op : ops) {
            Priority opPriority = opIdPriority.get(op.getId());
            for (Action ac : op.getActions()) {
                SlotSample ss = new SlotSample(ac.getDestination(), ac.getSample());
                if (PRIORITY_COMPARATOR.compare(ssPriorities.get(ss), opPriority) < 0) {
                    ssPriorities.put(ss, opPriority);
                }
            }
        }
        List<LabwareFlagged> lwFlagged = new ArrayList<>(labware.size());
        for (Labware lw : labware) {
            Priority priority = SlotSample.stream(lw)
                    .flatMap(ss -> ancestry.ancestors(ss).stream())
                    .map(ssPriorities::get)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
            lwFlagged.add(new LabwareFlagged(lw, priority));
        }
        return lwFlagged;
    }

    @Override
    public LabwareFlagged getLabwareFlagged(Labware lw) {
        return new LabwareFlagged(lw, labwareFlagPriority(lw));
    }

    /**
     * Converts a barcode and list of flags to a FlagDetail object
     * @param barcode the barcode of the labware
     * @param flags the flags applicable to the labware
     * @return a flagdetail summarising the flags for that labwares
     */
    FlagDetail toDetail(String barcode, List<LabwareFlag> flags) {
        List<FlagSummary> summaries = flags.stream()
                .map(flag -> new FlagSummary(flag.getLabware().getBarcode(), flag.getDescription(), flag.getPriority()))
                .collect(toList());
        return new FlagDetail(barcode, summaries);
    }

    /** A hash key comprising an op id and a labware id */
    record OpIdLwId(Integer opid, Integer lwId) {}
}
