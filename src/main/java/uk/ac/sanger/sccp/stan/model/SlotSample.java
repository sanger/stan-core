package uk.ac.sanger.sccp.stan.model;

import java.util.Objects;

/**
 * A utility struct of a slot and a sample.
 * @author dr6
 */
public class SlotSample {
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
        return (Objects.equals(this.slot, that.slot)
                && Objects.equals(this.sample, that.sample));
    }

    @Override
    public int hashCode() {
        return Objects.hash(slot, sample);
    }

    @Override
    public String toString() {
        return String.format("(%s, %s)", slot, sample);
    }
}
