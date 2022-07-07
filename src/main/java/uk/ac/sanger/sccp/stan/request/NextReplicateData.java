package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;
import java.util.Objects;

/**
 * The data about original tissues and their next replicate numbers.
 * @author dr6
 */
public class NextReplicateData {
    private List<String> barcodes;
    private int donorId;
    private int spatialLocationId;
    private int nextReplicateNumber;

    public NextReplicateData() {
        setBarcodes(null);
    }

    public NextReplicateData(List<String> barcodes, int donorId, int spatialLocationId, int nextReplicateNumber) {
        setBarcodes(barcodes);
        setDonorId(donorId);
        setSpatialLocationId(spatialLocationId);
        setNextReplicateNumber(nextReplicateNumber);
    }

    /**
     * The source barcodes for the new replicates.
     */
    public List<String> getBarcodes() {
        return this.barcodes;
    }

    public void setBarcodes(List<String> barcodes) {
        this.barcodes = (barcodes==null ? List.of() : barcodes);
    }

    public int getDonorId() {
        return this.donorId;
    }

    public void setDonorId(int donorId) {
        this.donorId = donorId;
    }

    public int getSpatialLocationId() {
        return this.spatialLocationId;
    }

    public void setSpatialLocationId(int spatialLocationId) {
        this.spatialLocationId = spatialLocationId;
    }

    /**
     * The next replicate number for this group.
     */
    public int getNextReplicateNumber() {
        return this.nextReplicateNumber;
    }

    public void setNextReplicateNumber(int nextReplicateNumber) {
        this.nextReplicateNumber = nextReplicateNumber;
    }

    @Override
    public String toString() {
        return BasicUtils.describe("NextReplicateData")
                .add("barcodes", barcodes)
                .add("donorId", donorId)
                .add("spatialLocationId", spatialLocationId)
                .add("nextReplicateNumber", nextReplicateNumber)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NextReplicateData that = (NextReplicateData) o;
        return (this.donorId == that.donorId
                && this.spatialLocationId == that.spatialLocationId
                && this.nextReplicateNumber == that.nextReplicateNumber
                && this.barcodes.equals(that.barcodes));
    }

    @Override
    public int hashCode() {
        return Objects.hash(donorId, spatialLocationId, nextReplicateNumber, barcodes);
    }
}