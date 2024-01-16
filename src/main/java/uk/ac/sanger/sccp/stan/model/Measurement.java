package uk.ac.sanger.sccp.stan.model;

import com.google.common.base.MoreObjects;

import javax.persistence.*;
import java.util.Objects;

/**
 * Some kind of measurement, such as a section thickness.
 * A measurement is typically recorded against sample or samples in a particular labware slot.
 * The name and value represent a key/value pair
 * @author dr6
 */
@Entity
public class Measurement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;
    private String value;
    private Integer sampleId;
    private Integer operationId;
    private Integer slotId;

    public Measurement() {}

    public Measurement(Integer id, String name, String value, Integer sampleId, Integer operationId, Integer slotId) {
        this.id = id;
        this.operationId = operationId;
        this.name = name;
        this.value = value;
        this.sampleId = sampleId;
        this.slotId = slotId;
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

    public String getValue() {
        return this.value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Integer getSampleId() {
        return this.sampleId;
    }

    public void setSampleId(Integer sampleId) {
        this.sampleId = sampleId;
    }

    public Integer getOperationId() {
        return this.operationId;
    }

    public void setOperationId(Integer operationId) {
        this.operationId = operationId;
    }

    public Integer getSlotId() {
        return this.slotId;
    }

    public void setSlotId(Integer slotId) {
        this.slotId = slotId;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("name", name)
                .add("value", value)
                .add("sampleId", sampleId)
                .add("operationId", operationId)
                .add("slotId", slotId)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Measurement that = (Measurement) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.name, that.name)
                && Objects.equals(this.value, that.value)
                && Objects.equals(this.sampleId, that.sampleId)
                && Objects.equals(this.operationId, that.operationId)
                && Objects.equals(this.slotId, that.slotId));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, value, sampleId, operationId, slotId);
    }
}
