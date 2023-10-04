package uk.ac.sanger.sccp.stan.model;

import javax.persistence.*;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.describe;

/**
 * A probe panel on a labware used in an operation
 * @author dr6
 */
@Entity
public class LabwareProbe {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    private ProbePanel probePanel;
    private Integer operationId;
    private Integer labwareId;
    private String lotNumber;
    private Integer plex;

    @Column(columnDefinition = "enum('SGP', 'Faculty')")
    @Enumerated(EnumType.STRING)
    private  SlideCosting costing;

    public LabwareProbe() {}

    public LabwareProbe(Integer id, ProbePanel probePanel, Integer operationId, Integer labwareId,
                        String lotNumber, Integer plex) {
        this(id, probePanel, operationId, labwareId, lotNumber, plex, null);
    }

    public LabwareProbe(Integer id, ProbePanel probePanel, Integer operationId, Integer labwareId,
                        String lotNumber, Integer plex, SlideCosting costing) {
        this.id = id;
        this.probePanel = probePanel;
        this.operationId = operationId;
        this.labwareId = labwareId;
        this.lotNumber = lotNumber;
        this.plex = plex;
        this.costing = costing;
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public ProbePanel getProbePanel() {
        return this.probePanel;
    }

    public void setProbePanel(ProbePanel probePanel) {
        this.probePanel = probePanel;
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

    public Integer getPlex() {
        return this.plex;
    }

    public void setPlex(Integer plex) {
        this.plex = plex;
    }

    public SlideCosting getCosting() {
        return costing;
    }

    public void setCosting(SlideCosting cost) {
        this.costing = cost;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LabwareProbe that = (LabwareProbe) o;
        return (Objects.equals(this.id, that.id)
                && Objects.equals(this.probePanel, that.probePanel)
                && Objects.equals(this.operationId, that.operationId)
                && Objects.equals(this.labwareId, that.labwareId)
                && Objects.equals(this.lotNumber, that.lotNumber)
                && Objects.equals(this.plex, that.plex)
                && Objects.equals(this.costing, that.costing));
    }

    @Override
    public int hashCode() {
        return (id!=null ? id.hashCode() : Objects.hash(probePanel, operationId, labwareId, costing));
    }

    @Override
    public String toString() {
        return describe(this)
                .add("id", id)
                .add("probePanel", probePanel)
                .add("operationId", operationId)
                .add("labwareId", labwareId)
                .addRepr("lotNumber", lotNumber)
                .add("plex", plex)
                .add("costing", costing)
                .toString();
    }
}
