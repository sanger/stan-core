package uk.ac.sanger.sccp.stan.model.slotcopyrecord;

import org.jetbrains.annotations.NotNull;

import javax.persistence.*;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * A key/value with an index, linked to a slot copy record
 * @author dr6
 */
@Entity
@Table(name="slot_copy_record_note")
public class SlotCopyRecordNote implements Comparable<SlotCopyRecordNote> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;
    private int valueIndex = 0;
    private String value;

    public SlotCopyRecordNote() {} // required

    public SlotCopyRecordNote(String name, int valueIndex, String value) {
        this.name = name;
        this.valueIndex = valueIndex;
        this.value = value;
    }

    public SlotCopyRecordNote(String name, String value) {
        this(name, 0, value);
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getValueIndex() {
        return this.valueIndex;
    }

    public void setValueIndex(int index) {
        this.valueIndex = index;
    }

    public String getValue() {
        return this.value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SlotCopyRecordNote that = (SlotCopyRecordNote) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.name, that.name)
                && this.valueIndex == that.valueIndex
                && Objects.equals(this.value, that.value));
    }

    @Override
    public int hashCode() {
        return id!=null ? id.hashCode() : Objects.hash(name, valueIndex, value);
    }

    @Override
    public String toString() {
        return String.format("[%s:%s=%s]", name, valueIndex, repr(value));
    }

    @Override
    public int compareTo(@NotNull SlotCopyRecordNote o) {
        int n = this.name.compareTo(o.name);
        if (n != 0) {
            return n;
        }
        return Integer.compare(this.valueIndex, o.valueIndex);
    }
}
