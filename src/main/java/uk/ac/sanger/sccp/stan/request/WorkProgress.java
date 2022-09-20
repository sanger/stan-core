package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.Work;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Information about the progress of a work.
 * Progress is indicated by a collection of timestamps associated with different named operations.
 * @author dr6
 */
public class WorkProgress {
    public static class WorkProgressTimestamp {
        private String type;
        private LocalDateTime timestamp;

        public WorkProgressTimestamp() {}

        public WorkProgressTimestamp(String type, LocalDateTime timestamp) {
            this.type = type;
            this.timestamp = timestamp;
        }

        public String getType() {
            return this.type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public LocalDateTime getTimestamp() {
            return this.timestamp;
        }

        public void setTimestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            WorkProgressTimestamp that = (WorkProgressTimestamp) o;
            return (Objects.equals(this.type, that.type)
                    && Objects.equals(this.timestamp, that.timestamp));
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, timestamp);
        }

        @Override
        public String toString() {
            return String.format("(%s: %s)", type, timestamp);
        }
    }

    private Work work;
    private List<WorkProgressTimestamp> timestamps;
    private String mostRecentOperation;
    private String workComment;

    public WorkProgress() {
        this(null);
    }

    public WorkProgress(Work work) {
        this.work = work;
        this.timestamps = new ArrayList<>();
        this.mostRecentOperation = null;
    }

    public WorkProgress(Work work, List<WorkProgressTimestamp> timestamps, String mostRecentOperation, String workComment) {
        this.work = work;
        this.timestamps = timestamps;
        this.mostRecentOperation = mostRecentOperation;
        this.workComment = workComment;
    }

    public Work getWork() {
        return this.work;
    }

    public void setWork(Work work) {
        this.work = work;
    }

    public List<WorkProgressTimestamp> getTimestamps() {
        return this.timestamps;
    }

    public void setTimestamps(List<WorkProgressTimestamp> timestamps) {
        this.timestamps = timestamps;
    }

    public String getMostRecentOperation() {
        return this.mostRecentOperation;
    }

    public void setMostRecentOperation(String mostRecentOperation) {
        this.mostRecentOperation = mostRecentOperation;
    }

    public String getWorkComment() {
        return this.workComment;
    }

    public void setWorkComment(String workComment) {
        this.workComment = workComment;
    }

    public void addTime(String type, LocalDateTime timestamp) {
        this.timestamps.add(new WorkProgressTimestamp(type, timestamp));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkProgress that = (WorkProgress) o;
        return (Objects.equals(this.work, that.work)
                && Objects.equals(this.timestamps, that.timestamps))
                && Objects.equals(this.mostRecentOperation, that.mostRecentOperation)
                && Objects.equals(this.workComment, that.workComment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(work, timestamps, mostRecentOperation, workComment);
    }

    @Override
    public String toString() {
        return String.format("WorkProgress(%s, %s, %s, %s)", work==null ? null : work.getWorkNumber(), timestamps, mostRecentOperation, workComment);
    }
}
