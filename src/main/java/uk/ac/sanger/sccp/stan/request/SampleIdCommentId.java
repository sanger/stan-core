package uk.ac.sanger.sccp.stan.request;

import java.util.Objects;

/**
 * A sample id and a comment id, for specifying comments in requests
 * @author dr6
 */
public class SampleIdCommentId {
    private Integer sampleId;
    private Integer commentId;

    public SampleIdCommentId() {}

    public SampleIdCommentId(Integer sampleId, Integer commentId) {
        this.sampleId = sampleId;
        this.commentId = commentId;
    }

    public Integer getSampleId() {
        return this.sampleId;
    }

    public void setSampleId(Integer sampleId) {
        this.sampleId = sampleId;
    }

    public Integer getCommentId() {
        return this.commentId;
    }

    public void setCommentId(Integer commentId) {
        this.commentId = commentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SampleIdCommentId that = (SampleIdCommentId) o;
        return (Objects.equals(this.sampleId, that.sampleId)
                && Objects.equals(this.commentId, that.commentId));
    }

    @Override
    public int hashCode() {
        return Objects.hash(sampleId, commentId);
    }

    @Override
    public String toString() {
        return String.format("(sampleId=%s, commentId=%s)", sampleId, commentId);
    }
}
