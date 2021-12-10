package uk.ac.sanger.sccp.stan.model;

import uk.ac.sanger.sccp.utils.BasicUtils;

import javax.persistence.*;
import java.util.Objects;

/**
 * A piece of named information recorded (as a string) against a particular piece of labware in an operation.
 * Like a {@link Measurement}, but not associated with a particular slot and sample.
 * @author dr6
 */
@Entity
public class LabwareNote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer labwareId;
    private Integer operationId;
    private String name;
    private String value;

    public LabwareNote() {}

    public LabwareNote(Integer id, Integer labwareId, Integer operationId, String name, String value) {
        this.id = id;
        this.labwareId = labwareId;
        this.operationId = operationId;
        this.name = name;
        this.value = value;
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getLabwareId() {
        return this.labwareId;
    }

    public void setLabwareId(Integer labwareId) {
        this.labwareId = labwareId;
    }

    public Integer getOperationId() {
        return this.operationId;
    }

    public void setOperationId(Integer operationId) {
        this.operationId = operationId;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LabwareNote that = (LabwareNote) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.labwareId, that.labwareId)
                && Objects.equals(this.operationId, that.operationId)
                && Objects.equals(this.name, that.name)
                && Objects.equals(this.value, that.value));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : Objects.hash(labwareId, operationId, name, value));
    }

    @Override
    public String toString() {
        return BasicUtils.describe("LabwareNote")
                .add("id", id)
                .add("labwareId", labwareId)
                .add("operationId", operationId)
                .addRepr("name", name)
                .addRepr("value", value)
                .toString();
    }
}
