package uk.ac.sanger.sccp.stan.service.releasefile;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.ActionRepo;

import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * Helper for getting a slot/sample's ancestry (that is, the slot/sample that was the source of an action
 * with the given destination).
 * @author dr6
 */
@Service
public class Ancestoriser {
    private final ActionRepo actionRepo;

    @Autowired
    public Ancestoriser(ActionRepo actionRepo) {
        this.actionRepo = actionRepo;
    }

    /**
     * Gets ancestry of each slot-sample to all of the slot-samples that went into it
     * (and all the recursive ancestry).
     * @param slotSamples the slot-samples to get the ancestry for
     * @return the ancestry for all the specified slot-samples
     */
    public Ancestry findAncestry(Collection<SlotSample> slotSamples) {
        Ancestry ancestry = new Ancestry();
        Set<SlotSample> newSlotSamples = new HashSet<>(slotSamples);
        Set<SlotSample> done = new HashSet<>();
        while (!newSlotSamples.isEmpty()) {
            Set<Slot> slots = newSlotSamples.stream().map(SlotSample::slot).collect(toSet());
            List<Action> actions = actionRepo.findAllByDestinationIn(slots);
            Map<Integer, List<Action>> destSlotIdActions = new HashMap<>();
            for (Action action : actions) {
                Integer destSlotId = action.getDestination().getId();
                if (destSlotId.equals(action.getSource().getId()) && action.getSample().getId().equals(action.getSourceSample().getId())) {
                    continue;
                }
                List<Action> ac = destSlotIdActions.computeIfAbsent(destSlotId, k -> new ArrayList<>());
                ac.add(action);
            }
            Set<SlotSample> lastSlotSamples = newSlotSamples;
            newSlotSamples = new HashSet<>();
            for (SlotSample slotSample : lastSlotSamples) {
                if (!done.add(slotSample)) {
                    continue;
                }
                Set<SlotSample> values = new TreeSet<>();
                ancestry.put(slotSample, values);
                List<Action> slotActions = destSlotIdActions.get(slotSample.slot.getId());
                if (slotActions==null) {
                    continue;
                }
                for (Action action : slotActions) {
                    if (action.getSample().equals(slotSample.sample)) {
                        SlotSample sourceSlotSample = new SlotSample(action.getSource(), action.getSourceSample());
                        newSlotSamples.add(sourceSlotSample);
                        values.add(sourceSlotSample);
                    }
                }
            }
            newSlotSamples.removeAll(done);
        }
        return ancestry;
    }

    /**
     * Gets posterity (all descendents) of the given slot samples.
     * @param slotSamples the slot samples to look up descendents of
     * @return the posterity for all the given slot samples
     */
    public Posterity findPosterity(Collection<SlotSample> slotSamples) {
        Posterity posterity = new Posterity();
        Set<SlotSample> newSlotSamples = new HashSet<>(slotSamples);
        Set<SlotSample> done = new HashSet<>();
        while (!newSlotSamples.isEmpty()) {
            Set<Slot> slots = newSlotSamples.stream().map(SlotSample::slot).collect(toSet());
            List<Action> actions = actionRepo.findAllBySourceIn(slots);
            Map<Integer, List<Action>> sourceSlotIdActions = new HashMap<>();
            for (Action action : actions) {
                Integer sourceSlotId = action.getSource().getId();
                if (sourceSlotId.equals(action.getDestination().getId()) && action.getSample().getId().equals(action.getSourceSample().getId())) {
                    continue;
                }
                List<Action> ac = sourceSlotIdActions.computeIfAbsent(sourceSlotId, k -> new ArrayList<>());
                ac.add(action);
            }
            Set<SlotSample> lastSlotSamples = newSlotSamples;
            newSlotSamples = new HashSet<>();
            for (SlotSample slotSample : lastSlotSamples) {
                if (!done.add(slotSample)) {
                    continue;
                }
                Set<SlotSample> values = new TreeSet<>();
                posterity.put(slotSample, values);
                List<Action> slotActions = sourceSlotIdActions.get(slotSample.slotId());
                if (nullOrEmpty(slotActions)) {
                    continue;
                }
                for (Action action : slotActions) {
                    if (action.getSourceSample().equals(slotSample.sample)) {
                        SlotSample destSlotSample = new SlotSample(action.getDestination(), action.getSample());
                        newSlotSamples.add(destSlotSample);
                        values.add(destSlotSample);
                    }
                }
            }
            newSlotSamples.removeAll(done);
        }
        return posterity;
    }

    /**
     * A representation of the ancestry of samples (through slots and other samples).
     * It is modelled something like a map with a slot-sample key mapping to the set
     * of slot-samples that are its direct sources.
     */
    public static class Ancestry {
        private final Map<SlotSample, Set<SlotSample>> map = new HashMap<>();

        /**
         * Gets the sources for the given destination slot-sample.
         * If the given key is not in this ancestry, an empty set will be returned
         * @param key a slot-sample
         * @return the sources of the given slot-sample
         */
        public Set<SlotSample> get(SlotSample key) {
            return map.getOrDefault(key, Set.of());
        }

        /**
         * Roots are the end-points of the ancestry that do not map to any more slot-samples.
         * @param branch a slot-sample
         * @return the roots of the given slot-sample in this ancestry
         */
        public Set<SlotSample> getRoots(SlotSample branch) {
            return endPoints(branch, map);
        }

        /**
         * Gets all the ancestor slot-samples of a specific slot-sample, recursing through
         * this ancestry until last root.
         * The returned set is ordered, most recent generation first
         * @param last the slot-sample to get the ancestors of
         * @return the ancestors of the given slot-sample (including itself)
         */
        public Set<SlotSample> ancestors(SlotSample last) {
            return followers(last, map);
        }

        /**
         * Gets the set of all the slot-samples which have their sources specified in this ancestry
         * @return the set of key slot-samples
         */
        public Set<SlotSample> keySet() {
            return map.keySet();
        }

        /**
         * Sets the sources for the given slot-sample key.
         * @param key the slot-sample
         * @param values the sources for the given key slot-sample
         * @return the previous value associated with the key
         */
        public Set<SlotSample> put(SlotSample key, Set<SlotSample> values) {
            return map.put(key, values);
        }
    }

    /**
     * A representation of the descendents of samples (through slots and other samples).
     * It is modelled something like a map with a slot-sample key mapping to the set
     * of slot-samples that are its direct destinations.
     */
    public static class Posterity {
        private final Map<SlotSample, Set<SlotSample>> map = new HashMap<>();

        /**
         * Gets the destinations for the given source slot-sample.
         * If the given key is not in this posterity, an empty set will be returned
         * @param key a slot-sample
         * @return the destinations of the given slot-sample
         */
        public Set<SlotSample> get(SlotSample key) {
            return map.getOrDefault(key, Set.of());
        }

        /**
         * Leafs are the end-points of the posterity that do not map to any more slot-samples.
         * @param branch a slot-sample
         * @return the leafs of the given slot-sample
         */
        public Set<SlotSample> getLeafs(SlotSample branch) {
            return endPoints(branch, map);
        }

        /**
         * Leafs are the end-points of the posterity that do not map to any more slot-samples.
         * @return all the leafs in the posterity
         */
        public Set<SlotSample> getLeafs() {
            Set<SlotSample> current = map.keySet();
            Set<SlotSample> leafs = new HashSet<>();
            Set<SlotSample> seen = new HashSet<>();
            while (!current.isEmpty()) {
                Set<SlotSample> previous = current;
                current = new HashSet<>();
                for (SlotSample ss : previous) {
                    seen.add(ss);
                    Set<SlotSample> values = map.get(ss);
                    if (nullOrEmpty(values)) {
                        leafs.add(ss);
                    } else {
                        values.stream().filter(v -> !seen.contains(v)).forEach(current::add);
                    }
                }
            }
            return leafs;
        }

        /**
         * Gets all the descendent slot-samples of a specific slot-sample, recursing through
         * this posterity.
         * The returned set is ordered, most recent generation first
         * @param start the slot-sample to get the descendents of
         * @return the descendents of the given slot-sample (including itself)
         */
        public Set<SlotSample> descendents(SlotSample start) {
            return followers(start, map);
        }

        /**
         * Gets the set of all the slot-samples which have their destinations specified in this ancestry
         * @return the set of key slot-samples
         */
        public Set<SlotSample> keySet() {
            return map.keySet();
        }

        /**
         * Sets the destinations for the given slot-sample key.
         * @param key the slot-sample
         * @param values the destinations for the given key slot-sample
         * @return the previous value associated with the key
         */
        public Set<SlotSample> put(SlotSample key, Set<SlotSample> values) {
            return map.put(key, values);
        }
    }

    private static <E> Set<E> endPoints(E start, Map<E, Set<E>> map) {
        Set<E> newBranches = Set.of(start);
        Set<E> ends = new HashSet<>();
        Set<E> done = new HashSet<>();
        while (!newBranches.isEmpty()) {
            Set<E> currentBranches = newBranches;
            newBranches = new HashSet<>();
            for (E ss : currentBranches) {
                if (!done.add(ss)) {
                    continue;
                }
                Set<E> children = map.get(ss);
                if (nullOrEmpty(children)) {
                    ends.add(ss);
                } else {
                    newBranches.addAll(children);
                }
            }
            newBranches.removeAll(done);
        }
        return ends;
    }

    private static <E> Set<E> followers(E start, Map<E, Set<E>> map) {
        Set<E> found = new LinkedHashSet<>();
        Set<E> current = new LinkedHashSet<>();
        current.add(start);
        while (!current.isEmpty()) {
            Set<E> old = current;
            current = new LinkedHashSet<>();
            for (E ss : old) {
                if (!found.add(ss)) {
                    continue;
                }
                Set<E> nextSs = map.get(ss);
                if (nextSs!=null) {
                    current.addAll(nextSs);
                }
            }
            current.removeAll(found);
        }
        return found;
    }

    /** A class representing just a slot and a sample, used in an ancestry map. */
    public record SlotSample(Slot slot, Sample sample) implements Comparable<SlotSample> {

        public Integer slotId() {
            return this.slot().getId();
        }

        public Integer sampleId() {
            return this.sample().getId();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SlotSample that = (SlotSample) o;
            return (slotId().equals(that.slotId()) && sampleId().equals(that.sampleId()));
        }

        @Override
        public int hashCode() {
            return 31*slotId() + sampleId();
        }

        @NotNull
        @Override
        public String toString() {
            return String.format("(Slot(%s), Sample(%s))", slotId(), sampleId());
        }

        @Override
        public int compareTo(@NotNull SlotSample that) {
            int n = Integer.compare(this.slotId(), that.slotId());
            if (n!=0) {
                return n;
            }
            return Integer.compare(this.sampleId(), that.sampleId());
        }

        public static Stream<SlotSample> stream(Stream<Slot> slots) {
            return slots.flatMap(slot -> slot.getSamples().stream()
                    .map(sam -> new SlotSample(slot, sam)));
        }

        public static Stream<SlotSample> stream(Labware lw) {
            return stream(lw.getSlots().stream());
        }
    }
}
