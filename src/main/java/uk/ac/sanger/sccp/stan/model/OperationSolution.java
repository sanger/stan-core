package uk.ac.sanger.sccp.stan.model;

import uk.ac.sanger.sccp.utils.BasicUtils;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * A link between operations and solutions
 * @author dr6
 */
@Entity
@IdClass(OperationSolution.OperationSolutionKey.class)
public class OperationSolution {
    @Id
    private Integer operationId;
    @Id
    private Integer solutionId;
    @Id
    private Integer labwareId;
    @Id
    private Integer sampleId;

    public OperationSolution() {}

    public OperationSolution(Integer operationId, Integer solutionId, Integer labwareId, Integer sampleId) {
        this.operationId = operationId;
        this.solutionId = solutionId;
        this.labwareId = labwareId;
        this.sampleId = sampleId;
    }

    public Integer getOperationId() {
        return this.operationId;
    }

    public void setOperationId(Integer operationId) {
        this.operationId = operationId;
    }

    public Integer getSolutionId() {
        return this.solutionId;
    }

    public void setSolutionId(Integer solutionId) {
        this.solutionId = solutionId;
    }

    public Integer getLabwareId() {
        return this.labwareId;
    }

    public void setLabwareId(Integer labwareId) {
        this.labwareId = labwareId;
    }

    public Integer getSampleId() {
        return this.sampleId;
    }

    public void setSampleId(Integer sampleId) {
        this.sampleId = sampleId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OperationSolution that = (OperationSolution) o;
        return (Objects.equals(this.operationId, that.operationId)
                && Objects.equals(this.solutionId, that.solutionId)
                && Objects.equals(this.labwareId, that.labwareId)
                && Objects.equals(this.sampleId, that.sampleId));
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationId, solutionId, labwareId, sampleId);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("OperationSolution")
                .add("operationId", operationId)
                .add("solutionId", solutionId)
                .add("labwareId", labwareId)
                .add("sampleId", sampleId)
                .toString();
    }

    /**
     * The composite key for OperationSolution.
     */
    public static class OperationSolutionKey implements Serializable {
        private Integer operationId;
        private Integer solutionId;
        private Integer labwareId;
        private Integer sampleId;

        public OperationSolutionKey() {}

        public OperationSolutionKey(Integer operationId, Integer solutionId, Integer labwareId, Integer sampleId) {
            this.operationId = operationId;
            this.solutionId = solutionId;
            this.labwareId = labwareId;
            this.sampleId = sampleId;
        }

        public Integer getOperationId() {
            return this.operationId;
        }

        public Integer getSolutionId() {
            return this.solutionId;
        }

        public Integer getLabwareId() {
            return this.labwareId;
        }

        public Integer getSampleId() {
            return this.sampleId;
        }

        public void setOperationId(Integer operationId) {
            this.operationId = operationId;
        }

        public void setSolutionId(Integer solutionId) {
            this.solutionId = solutionId;
        }

        public void setLabwareId(Integer labwareId) {
            this.labwareId = labwareId;
        }

        public void setSampleId(Integer sampleId) {
            this.sampleId = sampleId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OperationSolutionKey that = (OperationSolutionKey) o;
            return (Objects.equals(this.operationId, that.operationId)
                    && Objects.equals(this.solutionId, that.solutionId)
                    && Objects.equals(this.labwareId, that.labwareId)
                    && Objects.equals(this.sampleId, that.sampleId));
        }

        @Override
        public int hashCode() {
            return Objects.hash(operationId, solutionId, labwareId, sampleId);
        }
    }
}
