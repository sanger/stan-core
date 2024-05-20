package uk.ac.sanger.sccp.stan.model;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.describe;

/**
 * A metric recorded against a {@link Roi region of interest}
 * @author dr6
 */
@Entity
public class RoiMetric implements HasIntId, HasName {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private Integer id;
    private Integer labwareId;
    private Integer operationId;
    private String roi;
    private String name;
    private String value;
    private LocalDateTime deprecated;

    // Deserialisation constructor
    public RoiMetric() {}

    public RoiMetric(Integer id, Integer labwareId, Integer operationId, String roi, String name, String value,
                     LocalDateTime deprecated) {
        this.id = id;
        this.labwareId = labwareId;
        this.operationId = operationId;
        this.roi = roi;
        this.name = name;
        this.value = value;
        this.deprecated = deprecated;
    }

    public RoiMetric(Integer labwareId, Integer operationId, String roi, String name, String value) {
        this(null, labwareId, operationId, roi, name, value, null);
    }

    @Override
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

    public String getRoi() {
        return this.roi;
    }

    public void setRoi(String roi) {
        this.roi = roi;
    }

    @Override
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

    public LocalDateTime getDeprecated() {
        return this.deprecated;
    }

    public void setDeprecated(LocalDateTime deprecated) {
        this.deprecated = deprecated;
    }

    @Override
    public String toString() {
        return describe(this)
                .add("id", id)
                .add("labwareId", labwareId)
                .add("operationId", operationId)
                .add("roi", roi)
                .add("name", name)
                .add("value", value)
                .addIfNotNull("deprecated", deprecated==null ? null : deprecated.toString())
                .reprStringValues()
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoiMetric that = (RoiMetric) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.labwareId, that.labwareId)
                && Objects.equals(this.operationId, that.operationId)
                && Objects.equals(this.roi, that.roi)
                && Objects.equals(this.name, that.name)
                && Objects.equals(this.value, that.value)
                && Objects.equals(this.deprecated, that.deprecated)
        );
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : 0);
    }
}
