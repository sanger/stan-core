package uk.ac.sanger.sccp.stan.service.label;

import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.*;

/**
 * A collection of information that may be printed onto a labware label.
 * @author dr6
 */
public class LabwareLabelData {
    private final String barcode;
    private final String externalBarcode;
    private final String medium;
    private final String date;

    private final List<LabelContent> contents;

    public LabwareLabelData(String barcode, String externalBarcode, String medium, String date,
                            List<LabelContent> contents) {
        this.barcode = barcode;
        this.externalBarcode = externalBarcode;
        this.medium = medium;
        this.date = date;
        this.contents = List.copyOf(contents);
    }

    public String getBarcode() {
        return this.barcode;
    }

    public String getExternalBarcode() {
        return this.externalBarcode;
    }

    public String getMedium() {
        return this.medium;
    }

    public String getDate() {
        return this.date;
    }

    public List<LabelContent> getContents() {
        return this.contents;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LabwareLabelData that = (LabwareLabelData) o;
        return (Objects.equals(this.barcode, that.barcode)
                && Objects.equals(this.externalBarcode, that.externalBarcode)
                && Objects.equals(this.medium, that.medium)
                && Objects.equals(this.date, that.date)
                && Objects.equals(this.contents, that.contents));
    }

    @Override
    public int hashCode() {
        return barcode!=null ? barcode.hashCode() : 0;
    }

    @Override
    public String toString() {
        return BasicUtils.describe(this)
                .add("barcode", barcode)
                .add("externalBarcode", externalBarcode)
                .add("medium", medium)
                .add("date", date)
                .add("contents", contents)
                .omitNullValues()
                .reprStringValues()
                .toString();
    }

    public Map<String, String> getFields() {
        HashMap<String, String> fields = new HashMap<>(4 + 4 * contents.size());
        fields.put("barcode", getBarcode());
        fields.put("medium", getMedium());
        fields.put("date", getDate());
        fields.put("external", getExternalBarcode());
        int index = 0;
        for (LabelContent content : contents) {
            addField(fields, "donor", index, content.donorName());
            addField(fields, "tissue", index, content.tissueDesc());
            if (content.replicate()!=null) {
                addField(fields, "replicate", index, "R:"+content.replicate());
            }
            if (content.stateDesc()!=null) {
                addField(fields, "state", index, content.stateDesc());
            }
            ++index;
        }
        return fields;
    }

    private void addField(Map<String, String> map, String fieldName, int index, String value) {
        if (value!=null && !value.isEmpty()) {
            map.put(fieldName+"["+index+"]", value);
        }
    }

    public record LabelContent(String donorName, String tissueDesc, String replicate, String stateDesc) {
        public LabelContent(String donorName, String tissueDesc, String replicate) {
            this(donorName, tissueDesc, replicate, (String) null);
        }

        public LabelContent(String donorName, String tissueDesc, String replicate, Integer section) {
            this(donorName, tissueDesc, replicate, section==null ? null : String.format("S%03d", section));
        }

        public LabelContent(String donorName, String tissueDesc, String replicate, Integer minSection, Integer maxSection) {
            this(donorName, tissueDesc, replicate, minSection==null ? null :
                    String.format(maxSection==null || minSection.equals(maxSection) ? "S%03d" : "S%03d+", minSection));
        }

        public LabelContent withStateDesc(String newStateDesc) {
            return new LabelContent(this.donorName, this.tissueDesc, this.replicate, newStateDesc);
        }

        @Override
        public String toString() {
            return BasicUtils.describe(this)
                    .add("donorName", donorName)
                    .add("tissueDesc", tissueDesc)
                    .add("replicate", replicate)
                    .add("stateDesc", stateDesc)
                    .omitNullValues()
                    .reprStringValues()
                    .toString();
        }
    }
}
