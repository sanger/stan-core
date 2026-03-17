package uk.ac.sanger.sccp.stan.service.block;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.request.TissueBlockRequest.TissueBlockContent;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;
import java.util.Objects;

/**
 * @author dr6
 */
public class BlockData {
    private TissueBlockContent requestContent;
    private Labware sourceLabware;
    private Sample sourceSample;
    private Sample sample;
    private List<Slot> sourceSlots;
    private List<Slot> destSlots;
    private Comment comment;

    public BlockData(TissueBlockContent requestContent) {
        this.requestContent = requestContent;
    }

    public TissueBlockContent getRequestContent() {
        return this.requestContent;
    }

    public void setRequestContent(TissueBlockContent requestContent) {
        this.requestContent = requestContent;
    }

    public Labware getSourceLabware() {
        return this.sourceLabware;
    }

    public void setSourceLabware(Labware sourceLabware) {
        this.sourceLabware = sourceLabware;
    }

    public Sample getSourceSample() {
        return this.sourceSample;
    }

    public void setSourceSample(Sample sourceSample) {
        this.sourceSample = sourceSample;
    }

    public Sample getSample() {
        return this.sample;
    }

    public void setSample(Sample sample) {
        this.sample = sample;
    }

    public List<Slot> getSourceSlots() {
        return this.sourceSlots;
    }

    public void setSourceSlots(List<Slot> sourceSlots) {
        this.sourceSlots = sourceSlots;
    }

    public List<Slot> getDestSlots() {
        return this.destSlots;
    }

    public void setDestSlots(List<Slot> destSlots) {
        this.destSlots = destSlots;
    }

    public Comment getComment() {
        return this.comment;
    }

    public void setComment(Comment comment) {
        this.comment = comment;
    }

    @Override
    public String toString() {
        return BasicUtils.describe(this)
                .add("requestContent", requestContent)
                .add("sourceSample", sourceSample)
                .add("sample", sample)
                .add("sourceSlots", sourceSlots)
                .add("destSlots", destSlots)
                .add("comment", comment)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        BlockData that = (BlockData) o;
        return (Objects.equals(this.requestContent, that.requestContent)
                && Objects.equals(this.sourceSample, that.sourceSample)
                && Objects.equals(this.sample, that.sample)
                && Objects.equals(this.sourceSlots, that.sourceSlots)
                && Objects.equals(this.destSlots, that.destSlots)
                && Objects.equals(this.comment, that.comment)
        );
    }

    @Override
    public int hashCode() {
        return requestContent.hashCode();
    }
}
