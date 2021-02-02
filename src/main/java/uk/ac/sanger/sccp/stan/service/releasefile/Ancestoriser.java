package uk.ac.sanger.sccp.stan.service.releasefile;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.ActionRepo;

import java.util.*;

import static java.util.stream.Collectors.toSet;

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

    public Map<SlotSample, SlotSample> findAncestry(Collection<SlotSample> slotSamples) {
        Map<SlotSample, SlotSample> ancestry = new HashMap<>();
        Set<SlotSample> newSlotSamples = new HashSet<>(slotSamples);
        Set<SlotSample> done = new HashSet<>();
        while (!newSlotSamples.isEmpty()) {
            Set<Slot> slots = newSlotSamples.stream().map(SlotSample::getSlot).collect(toSet());
            List<Action> actions = actionRepo.findAllByDestinationIn(slots);
            Map<Integer, List<Action>> destSlotIdActions = new HashMap<>();
            for (Action action : actions) {
                Integer destSlotId = action.getDestination().getId();
                List<Action> ac = destSlotIdActions.computeIfAbsent(destSlotId, k -> new ArrayList<>());
                ac.add(action);
            }
            Set<SlotSample> lastSlotSamples = newSlotSamples;
            newSlotSamples = new HashSet<>();
            for (SlotSample slotSample : lastSlotSamples) {
                if (!done.add(slotSample)) {
                    continue;
                }
                List<Action> slotActions = destSlotIdActions.get(slotSample.slot.getId());
                for (Action action : slotActions) {
                    if (action.getSample().equals(slotSample.sample)) {
                        SlotSample sourceSlotSample = source(action);
                        if (sourceSlotSample != null) {
                            newSlotSamples.add(sourceSlotSample);
                            ancestry.put(slotSample, sourceSlotSample);
                            break;
                        }
                    }
                }
            }
            newSlotSamples.removeAll(done);
        }
        return ancestry;
    }

    public SlotSample source(Action action) {
        Slot source = action.getSource();
        Sample sample = action.getSample();
        for (Sample sam : source.getSamples()) {
            if (sam.getId().equals(sample.getId())) {
                return new SlotSample(source, sam);
            }
        }
        Integer destTissueId = sample.getTissue().getId();
        for (Sample sam : source.getSamples()) {
            if (sam.getSection()==null && sam.getTissue().getId().equals(destTissueId)) {
                return new SlotSample(source, sam);
            }
        }
        // give up
        return null;
    }

    public static class SlotSample {
        private final Slot slot;
        private final Sample sample;

        public SlotSample(Slot slot, Sample sample) {
            this.slot = slot;
            this.sample = sample;
        }

        public Slot getSlot() {
            return this.slot;
        }

        public Sample getSample() {
            return this.sample;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SlotSample that = (SlotSample) o;
            return (this.slot.getId().equals(that.slot.getId()) && this.sample.getId().equals(that.sample.getId()));
        }

        @Override
        public int hashCode() {
            return 31*slot.getId() + sample.getId();
        }
    }
}
