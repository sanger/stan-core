package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.SlideCosting;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.nullToEmpty;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * Request to record an operation with probe panels.
 * @author dr6
 */
public class ProbeOperationRequest {
    // region nested classes
    /**
     * Labware in a probe operation request.
     * @author dr6
     */
    public static class ProbeOperationLabware {
        private String barcode;
        private String workNumber;
        private SlideCosting kitCosting;
        private String reagentLot;
        private List<ProbeLot> probes = List.of();
        private String spike;

        public ProbeOperationLabware() {}

        public ProbeOperationLabware(String barcode, String workNumber, SlideCosting kitCosting, String reagentLot,
                                     List<ProbeLot> probes, String spike) {
            setBarcode(barcode);
            setWorkNumber(workNumber);
            setKitCosting(kitCosting);
            setReagentLot(reagentLot);
            setProbes(probes);
            setSpike(spike);
        }

        /**
         * The barcode of the labware.
         */
        public String getBarcode() {
            return this.barcode;
        }

        public void setBarcode(String barcode) {
            this.barcode = barcode;
        }


        /**
         * The work number of the operation on this labware
         */
        public String getWorkNumber() {
            return this.workNumber;
        }

        public void setWorkNumber(String workNumber) {
            this.workNumber = workNumber;
        }

        /** The costing for the kit used on this labware. */
        public SlideCosting getKitCosting() {
            return this.kitCosting;
        }

        public void setKitCosting(SlideCosting kitCosting) {
            this.kitCosting = kitCosting;
        }

        /** Reagent lot number. */
        public String getReagentLot() {
            return this.reagentLot;
        }

        public void setReagentLot(String reagentLot) {
            this.reagentLot = reagentLot;
        }

        /**
         * The probes used on this labware.
         */
        public List<ProbeLot> getProbes() {
            return this.probes;
        }

        public void setProbes(List<ProbeLot> probes) {
            this.probes = nullToEmpty(probes);
        }

        public String getSpike() {
            return this.spike;
        }

        public void setSpike(String spike) {
            this.spike = spike;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ProbeOperationLabware that = (ProbeOperationLabware) o;
            return (Objects.equals(this.barcode, that.barcode)
                    && Objects.equals(this.workNumber, that.workNumber)
                    && this.kitCosting==that.kitCosting
                    && Objects.equals(this.reagentLot, that.reagentLot)
                    && Objects.equals(this.probes, that.probes)
                    && Objects.equals(this.spike, that.spike)
            );
        }

        @Override
        public int hashCode() {
            return (barcode!=null ? barcode.hashCode() : 0);
        }

        @Override
        public String toString() {
            return String.format("(%s, %s, workNumber: %s, reagentLot: %s, probes: %s, spike: %s)",
                    repr(barcode), kitCosting, repr(workNumber), repr(reagentLot), probes, repr(spike));
        }
    }

    /**
     * The probe used on a piece of labware in an operation.
     * @author dr6
     */
    public static class ProbeLot {
        private String name;
        private String lot;
        private Integer plex;

        private SlideCosting costing;

        // Deserialisation constructor
        public ProbeLot() {}

        public ProbeLot(String name, String lot, Integer plex, SlideCosting costing) {
            this.name = name;
            this.lot = lot;
            this.plex = plex;
            this.costing = costing;
        }

        /**
         * The name of the probe panel.
         */
        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        /**
         * The lot number of the probe.
         */
        public String getLot() {
            return this.lot;
        }

        public void setLot(String lot) {
            this.lot = lot;
        }

        /**
         * The plex number.
         */
        public Integer getPlex() {
            return this.plex;
        }

        public void setPlex(Integer plex) {
            this.plex = plex;
        }

        public SlideCosting getCosting() {
            return costing;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ProbeLot that = (ProbeLot) o;
            return (Objects.equals(this.name, that.name)
                    && Objects.equals(this.lot, that.lot)
                    && Objects.equals(this.plex, that.plex)
                    && Objects.equals(this.costing, that.costing));
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, lot, plex, costing);
        }

        @Override
        public String toString() {
            return String.format("(%s, %s, %s, %s)", repr(name), repr(lot), plex, costing==null ? null : repr(costing.name()));
        }
    }
    // endregion
    private String operationType;
    private LocalDateTime performed;
    private List<ProbeOperationLabware> labware = List.of();

    public ProbeOperationRequest() {}

    public ProbeOperationRequest(String operationType, LocalDateTime performed, List<ProbeOperationLabware> labware) {
        setOperationType(operationType);
        setPerformed(performed);
        setLabware(labware);
    }

    /**
     * The name of the type of operation.
     */
    public String getOperationType() {
        return this.operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    /**
     * The time when the operation was performed.
     */
    public LocalDateTime getPerformed() {
        return this.performed;
    }

    public void setPerformed(LocalDateTime performed) {
        this.performed = performed;
    }

    /**
     * The labware involved in the operation.
     */
    public List<ProbeOperationLabware> getLabware() {
        return this.labware;
    }

    public void setLabware(List<ProbeOperationLabware> labware) {
        this.labware = nullToEmpty(labware);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProbeOperationRequest that = (ProbeOperationRequest) o;
        return (Objects.equals(this.operationType, that.operationType)
                && Objects.equals(this.performed, that.performed)
                && Objects.equals(this.labware, that.labware));
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationType, performed, labware);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("ProbeOperationRequest")
                .add("operationType", operationType)
                .add("performed", performed)
                .add("labware", labware)
                .reprStringValues()
                .toString();
    }
}
