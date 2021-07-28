package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.utils.BasicUtils;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Information for a row in the history table
 * @author dr6
 */
public class HistoryEntry {
    private int eventId;
    private String type;
    private LocalDateTime time;
    private int sourceLabwareId;
    private int destinationLabwareId;
    private Integer sampleId;
    private final List<String> details = new ArrayList<>();

    public HistoryEntry(int eventId, String type, LocalDateTime time, int sourceLabwareId, int destinationLabwareId,
                        Integer sampleId, Collection<String> details) {
        this.eventId = eventId;
        this.type = type;
        this.time = time;
        this.sourceLabwareId = sourceLabwareId;
        this.destinationLabwareId = destinationLabwareId;
        this.sampleId = sampleId;
        setDetails(details);
    }

    public HistoryEntry(int eventId, String type, LocalDateTime time, int sourceLabwareId, int destinationLabwareId,
                        Integer sampleId) {
        this(eventId, type, time, sourceLabwareId, destinationLabwareId, sampleId, null);
    }

    public HistoryEntry() {}

    public int getEventId() {
        return this.eventId;
    }

    public void setEventId(int eventId) {
        this.eventId = eventId;
    }

    public String getType() {
        return this.type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public LocalDateTime getTime() {
        return this.time;
    }

    public void setTime(LocalDateTime time) {
        this.time = time;
    }

    public int getSourceLabwareId() {
        return this.sourceLabwareId;
    }

    public void setSourceLabwareId(int sourceLabwareId) {
        this.sourceLabwareId = sourceLabwareId;
    }

    public int getDestinationLabwareId() {
        return this.destinationLabwareId;
    }

    public void setDestinationLabwareId(int destinationLabwareId) {
        this.destinationLabwareId = destinationLabwareId;
    }

    public Integer getSampleId() {
        return this.sampleId;
    }

    public void setSampleId(Integer sampleId) {
        this.sampleId = sampleId;
    }

    public List<String> getDetails() {
        return this.details;
    }

    public void setDetails(Collection<String> details) {
        if (details != this.details) {
            this.details.clear();
            if (details != null) {
                this.details.addAll(details);
            }
        }
    }

    public void addDetail(String detail) {
        this.details.add(detail);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HistoryEntry that = (HistoryEntry) o;
        return (this.eventId==that.eventId
                && this.sourceLabwareId==that.sourceLabwareId
                && this.destinationLabwareId==that.destinationLabwareId
                && Objects.equals(this.sampleId, that.sampleId)
                && Objects.equals(this.type, that.type)
                && Objects.equals(this.time, that.time)
                && Objects.equals(this.details, that.details));
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new int[] { this.eventId, this.sourceLabwareId, this.destinationLabwareId,
                this.sampleId==null ? 0 : this.sampleId });
    }

    @Override
    public String toString() {
        return BasicUtils.describe("HistoryEntry")
                .add("eventId", eventId)
                .addRepr("type", type)
                .add("time", time==null ? null : time.toString())
                .add("sourceLabwareId", sourceLabwareId)
                .add("destinationLabwareId", destinationLabwareId)
                .add("sampleId", sampleId)
                .add("details", details)
                .toString();
    }
}