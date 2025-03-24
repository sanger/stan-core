package uk.ac.sanger.sccp.stan.model.slotcopyrecord;

import uk.ac.sanger.sccp.stan.model.OperationType;
import uk.ac.sanger.sccp.stan.model.Work;
import uk.ac.sanger.sccp.utils.BasicUtils;

import javax.persistence.*;
import java.util.*;

/**
 * A record of a saved slot copy request
 * @author dr6
 */
@Entity
public class SlotCopyRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne
    private OperationType operationType;
    @ManyToOne
    private Work work;
    private String lpNumber;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "slot_copy_record_id", nullable = false)
    private Set<SlotCopyRecordNote> notes = new HashSet<>();

    // Required no-arg constructor
    public SlotCopyRecord() {}

    public SlotCopyRecord(OperationType operationType, Work work, String lpNumber) {
        this.operationType = operationType;
        this.work = work;
        this.lpNumber = lpNumber;
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public OperationType getOperationType() {
        return this.operationType;
    }

    public void setOperationType(OperationType operationType) {
        this.operationType = operationType;
    }

    public Work getWork() {
        return this.work;
    }

    public void setWork(Work work) {
        this.work = work;
    }
    public String getLpNumber() {
        return this.lpNumber;
    }

    public void setLpNumber(String lpNumber) {
        this.lpNumber = lpNumber;
    }

    public Set<SlotCopyRecordNote> getNotes() {
        return this.notes;
    }

    public void setNotes(Collection<SlotCopyRecordNote> notes) {
        this.notes.clear();
        if (notes != null) {
            this.notes.addAll(notes);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SlotCopyRecord that = (SlotCopyRecord) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.operationType, that.operationType)
                && Objects.equals(this.work, that.work)
                && Objects.equals(this.lpNumber, that.lpNumber)
                && Objects.equals(this.notes, that.notes)
        );
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : Objects.hash(work, lpNumber));
    }

    @Override
    public String toString() {
        return BasicUtils.describe(this)
                .add("id", id)
                .add("operationType", operationType)
                .add("work", work)
                .add("lpNumber", lpNumber)
                .add("notes", notes)
                .toString();
    }
}
