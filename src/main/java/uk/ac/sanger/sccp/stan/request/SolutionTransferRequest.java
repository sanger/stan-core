package uk.ac.sanger.sccp.stan.request;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.describe;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * A request to perform solution transfer.
 * @author dr6
 */
public class SolutionTransferRequest {
    private String workNumber;
    private List<SolutionTransferLabware> labware;

    public SolutionTransferRequest() {
        this(null, null);
    }

    public SolutionTransferRequest(String workNumber, List<SolutionTransferLabware> labware) {
        setWorkNumber(workNumber);
        setLabware(labware);
    }

    /**
     * The work number for the operations.
     */
    public String getWorkNumber() {
        return this.workNumber;
    }

    public void setWorkNumber(String workNumber) {
        this.workNumber = workNumber;
    }

    /**
     * The details of the labware in the request.
     */
    public List<SolutionTransferLabware> getLabware() {
        return this.labware;
    }

    public void setLabware(List<SolutionTransferLabware> labware) {
        this.labware = (labware==null ? List.of() : labware);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SolutionTransferRequest that = (SolutionTransferRequest) o;
        return (Objects.equals(this.workNumber, that.workNumber)
                && Objects.equals(this.labware, that.labware));
    }

    @Override
    public int hashCode() {
        return Objects.hash(workNumber, labware);
    }

    @Override
    public String toString() {
        return describe("SolutionTransferRequest")
                .addRepr("workNumber", workNumber)
                .add("labware", labware)
                .toString();
    }

    /**
     * A labware in a solution transfer request.
     * @author dr6
     */
    public static class SolutionTransferLabware {
        private String barcode;
        private String solution;

        public SolutionTransferLabware() {}

        public SolutionTransferLabware(String barcode, String solution) {
            this.barcode = barcode;
            this.solution = solution;
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
         * The name solution.
         */
        public String getSolution() {
            return this.solution;
        }

        public void setSolution(String solution) {
            this.solution = solution;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SolutionTransferLabware that = (SolutionTransferLabware) o;
            return (Objects.equals(this.barcode, that.barcode)
                    && Objects.equals(this.solution, that.solution));
        }

        @Override
        public int hashCode() {
            return Objects.hash(barcode, solution);
        }

        @Override
        public String toString() {
            return String.format("(barcode=%s, solution=%s)", repr(barcode), repr(solution));
        }
    }
}
