package uk.ac.sanger.sccp.stan.request.register;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.describe;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullToEmpty;

/**
 * A request to register in a piece of labware containing one or more block-samples.
 * @author dr6
 */
public class BlockRegisterLabware {
    private String labwareType;
    private String medium;
    private String fixative;
    private String externalBarcode;
    private List<BlockRegisterSample> samples = List.of();

    /** The name of the type of labware containing the block. */
    public String getLabwareType() {
        return this.labwareType;
    }

    public void setLabwareType(String labwareType) {
        this.labwareType = labwareType;
    }

    /** The medium used for the tissue. */
    public String getMedium() {
        return this.medium;
    }

    public void setMedium(String medium) {
        this.medium = medium;
    }

    /** The fixative used for the tissue. */
    public String getFixative() {
        return this.fixative;
    }

    public void setFixative(String fixative) {
        this.fixative = fixative;
    }

    /** The external barcode of the labware. */
    public String getExternalBarcode() {
        return this.externalBarcode;
    }

    public void setExternalBarcode(String externalBarcode) {
        this.externalBarcode = externalBarcode;
    }

    /** The samples in this block. */
    public List<BlockRegisterSample> getSamples() {
        return this.samples;
    }

    public void setSamples(List<BlockRegisterSample> samples) {
        this.samples = nullToEmpty(samples);
    }

    @Override
    public String toString() {
        return describe(this)
                .add("labwareType", labwareType)
                .add("medium", medium)
                .add("fixative", fixative)
                .add("externalBarcode", externalBarcode)
                .add("samples", samples)
                .reprStringValues()
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || o.getClass() != this.getClass()) return false;
        BlockRegisterLabware that = (BlockRegisterLabware) o;
        return (Objects.equals(this.labwareType, that.labwareType)
                && Objects.equals(this.medium, that.medium)
                && Objects.equals(this.fixative, that.fixative)
                && Objects.equals(this.externalBarcode, that.externalBarcode)
                && Objects.equals(this.samples, that.samples)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(labwareType, medium, fixative, externalBarcode, samples);
    }
}