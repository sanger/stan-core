package uk.ac.sanger.sccp.stan.model;

import uk.ac.sanger.sccp.utils.BasicUtils;

import javax.persistence.*;
import java.util.Objects;

/**
 * Links a protein panel, op, labware, lot number, costing
 * @author dr6
 */
@Entity
public class OpPanel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne
    private ProteinPanel proteinPanel;
    private Integer operationId;
    private Integer labwareId;
    private String lotNumber;
    @Column(columnDefinition = "enum('SGP', 'Faculty', 'Warranty_replacement')")
    @Enumerated(EnumType.STRING)
    private SlideCosting costing;

    public OpPanel() {} // required no-arg constructor

    public OpPanel(Integer id, ProteinPanel proteinPanel, Integer operationId, Integer labwareId, String lotNumber,
                   SlideCosting costing) {
        this.id = id;
        this.proteinPanel = proteinPanel;
        this.operationId = operationId;
        this.labwareId = labwareId;
        this.lotNumber = lotNumber;
        this.costing = costing;
    }

    public OpPanel(ProteinPanel proteinPanel, Integer operationId, Integer labwareId, String lotNumber,
                   SlideCosting costing) {
        this(null, proteinPanel, operationId, labwareId, lotNumber, costing);
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public ProteinPanel getProteinPanel() {
        return this.proteinPanel;
    }

    public void setProteinPanel(ProteinPanel proteinPanel) {
        this.proteinPanel = proteinPanel;
    }

    public Integer getOperationId() {
        return this.operationId;
    }

    public void setOperationId(Integer operationId) {
        this.operationId = operationId;
    }

    public Integer getLabwareId() {
        return this.labwareId;
    }

    public void setLabwareId(Integer labwareId) {
        this.labwareId = labwareId;
    }

    public String getLotNumber() {
        return this.lotNumber;
    }

    public void setLotNumber(String lotNumber) {
        this.lotNumber = lotNumber;
    }

    public SlideCosting getCosting() {
        return this.costing;
    }

    public void setCosting(SlideCosting costing) {
        this.costing = costing;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        OpPanel that = (OpPanel) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.proteinPanel, that.proteinPanel)
                && Objects.equals(this.operationId, that.operationId)
                && Objects.equals(this.labwareId, that.labwareId)
                && Objects.equals(this.lotNumber, that.lotNumber)
                && this.costing == that.costing
        );
    }

    @Override
    public int hashCode() {
        return (id != null ? id.hashCode() : Objects.hash(proteinPanel, operationId, labwareId));
    }

    @Override
    public String toString() {
        return BasicUtils.describe(this)
                .add("id", id)
                .add("proteinPanel", proteinPanel)
                .add("operationId", operationId)
                .add("labwareId", labwareId)
                .addRepr("lotNumber", lotNumber)
                .add("costing", costing)
                .toString();
    }
}
