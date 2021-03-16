package uk.ac.sanger.sccp.stan.request.register;

import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.newArrayList;

/**
 * The labware (containing sections to be registered) in a section register request.
 * @author dr6
 */
public class SectionRegisterLabware {
    private String externalBarcode;
    private String labwareType;
    private List<SectionRegisterContent> contents;

    public SectionRegisterLabware() {
        this(null, null, null);
    }

    public SectionRegisterLabware(String externalBarcode, String labwareType, Collection<SectionRegisterContent> contents) {
        this.externalBarcode = externalBarcode;
        this.labwareType = labwareType;
        setContents(contents);
    }

    public String getExternalBarcode() {
        return this.externalBarcode;
    }

    public void setExternalBarcode(String externalBarcode) {
        this.externalBarcode = externalBarcode;
    }

    public String getLabwareType() {
        return this.labwareType;
    }

    public void setLabwareType(String labwareType) {
        this.labwareType = labwareType;
    }

    public List<SectionRegisterContent> getContents() {
        return this.contents;
    }

    public void setContents(Collection<SectionRegisterContent> contents) {
        this.contents = newArrayList(contents);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SectionRegisterLabware that = (SectionRegisterLabware) o;
        return (Objects.equals(this.externalBarcode, that.externalBarcode)
                && Objects.equals(this.labwareType, that.labwareType)
                && Objects.equals(this.contents, that.contents));
    }

    @Override
    public int hashCode() {
        return (externalBarcode!=null ? externalBarcode.hashCode() : 0);
    }

    @Override
    public String toString() {
        return BasicUtils.describe("SectionRegisterLabware")
                .add("externalBarcode", externalBarcode)
                .add("labwareType", labwareType)
                .add("contents", contents)
                .reprStringValues()
                .toString();
    }
}
