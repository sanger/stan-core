package uk.ac.sanger.sccp.stan.service.operation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.SampleRepo;
import uk.ac.sanger.sccp.stan.repo.SlotRepo;

import java.util.*;

/**
 * Helper service to update bio states in place in a piece of labware
 * @author dr6
 */
@Service
public class BioStateReplacer {
    private final SlotRepo slotRepo;
    private final SampleRepo sampleRepo;

    @Autowired
    public BioStateReplacer(SlotRepo slotRepo, SampleRepo sampleRepo) {
        this.slotRepo = slotRepo;
        this.sampleRepo = sampleRepo;
    }

    /**
     * Updates the labware affected by the given op type being recorded on it.
     * If any samples are updated, returns <i>all</i> the appropriate actions for the operation, linking
     * all old samples to their new counterparts.
     * If <tt>newBioState</tt> is null, returns null.
     * @param newBioState the new bio state (if any)
     * @param lw the labware affected
     * @return a list of actions describing the source and destination samples in their appropriate slots
     */
    public List<Action> updateBioStateInPlace(BioState newBioState, Labware lw) {
        if (newBioState==null) {
            return null;
        }
        final List<Action> actions = new ArrayList<>();
        Map<Integer, Sample> newSamples = new HashMap<>();
        for (Slot slot : lw.getSlots()) {
            if (slot.getSamples().stream().allMatch(sam -> newBioState.equals(sam.getBioState()))) {
                for (Sample sample : slot.getSamples()) {
                    actions.add(new Action(null, null, slot, slot, sample, sample));
                }
            } else {
                final List<Sample> newSlotSamples = new ArrayList<>(slot.getSamples().size());
                for (Sample oldSample : slot.getSamples()) {
                    Sample newSample = replaceSample(newBioState, oldSample, newSamples);
                    actions.add(new Action(null, null, slot, slot, newSample, oldSample));
                    newSlotSamples.add(newSample);
                }
                slot.setSamples(newSlotSamples);
                slotRepo.save(slot);
            }
        }
        return actions;
    }

    /**
     * Gets a sample of the given biostate to replace the given sample.
     * The sampleMap is a cache of old sample id to new sample.
     * If the old sample already has the specified bio state, it is returned.
     * Otherwise, if the old sample id already has a new sample in the cache, that is returned.
     * Otherwise, a new sample is created, added to the cache and returned.
     * @param bs the required bio state
     * @param oldSample the sample being replaced
     * @param sampleMap a map of old sample id to new (replacement) sample
     * @return the appropriate sample, in the correct bio state
     */
    public Sample replaceSample(BioState bs, Sample oldSample, Map<Integer, Sample> sampleMap) {
        if (bs.equals(oldSample.getBioState())) {
            return oldSample;
        }
        Sample newSample = sampleMap.get(oldSample.getId());
        if (newSample==null) {
            newSample = sampleRepo.save(new Sample(null, oldSample.getSection(), oldSample.getTissue(), bs));
            sampleMap.put(oldSample.getId(), newSample);
        }
        return newSample;
    }
}
