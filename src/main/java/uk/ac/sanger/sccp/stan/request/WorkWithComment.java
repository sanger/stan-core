package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.Work;

import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * A work instance along with an optional comment
 * @author dr6
 */
public class WorkWithComment {
    private Work work;
    private String comment;

    public WorkWithComment() {}

    public WorkWithComment(Work work, String comment) {
        this.work = work;
        this.comment = comment;
    }

    public WorkWithComment(Work work) {
        this(work, null);
    }

    public Work getWork() {
        return this.work;
    }

    public void setWork(Work work) {
        this.work = work;
    }

    public String getComment() {
        return this.comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkWithComment that = (WorkWithComment) o;
        return (Objects.equals(this.work, that.work)
                && Objects.equals(this.comment, that.comment));
    }

    @Override
    public int hashCode() {
        return Objects.hash(work, comment);
    }

    @Override
    public String toString() {
        return String.format("(work: %s, comment: %s)", work==null ? null : work.getWorkNumber(), repr(comment));
    }
}
