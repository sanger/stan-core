package uk.ac.sanger.sccp.stan.service.label;

import org.jetbrains.annotations.NotNull;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.nullToEmpty;

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
    private final Map<String, String> extraFields;

    public LabwareLabelData(String barcode, String externalBarcode, String medium, String date,
                            List<LabelContent> contents, Map<String, String> extraFields) {
        this.barcode = barcode;
        this.externalBarcode = externalBarcode;
        this.medium = medium;
        this.date = date;
        this.contents = nullToEmpty(contents);
        this.extraFields = nullToEmpty(extraFields);
    }

    public LabwareLabelData(String barcode, String externalBarcode, String medium, String date,
                            List<LabelContent> contents) {
        this(barcode, externalBarcode, medium, date, contents, null);
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

    public Map<String, String> getExtraFields() {
        return this.extraFields;
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
                && Objects.equals(this.contents, that.contents)
                && Objects.equals(this.extraFields, that.extraFields)
        );
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
                .add("extraFields", extraFields)
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
        fields.putAll(getExtraFields());
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

    public record LabelContent(String donorName, String externalName, String tissueDesc, String replicate, String stateDesc) {
        public LabelContent(String donorName, String tissueDesc, String replicate) {
            this(donorName, null, tissueDesc, replicate, null);
        }

        public LabelContent(String donorName, String tissueDesc, String replicate, Integer section) {
            this(donorName, null, tissueDesc, replicate, section==null ? null : String.format("S%03d", section));
        }

        public LabelContent(String donorName, String tissueDesc, String replicate, Integer minSection, Integer maxSection) {
            this(donorName, null,  tissueDesc, replicate, minSection==null ? null :
                    String.format(maxSection==null || minSection.equals(maxSection) ? "S%03d" : "S%03d+", minSection));
        }

        public LabelContent withStateDesc(String newStateDesc) {
            return new LabelContent(this.donorName, null, this.tissueDesc, this.replicate, newStateDesc);
        }

        @NotNull
        @Override
        public String toString() {
            return BasicUtils.describe(this)
                    .add("donorName", donorName)
                    .add("externalName", externalName)
                    .add("tissueDesc", tissueDesc)
                    .add("replicate", replicate)
                    .add("stateDesc", stateDesc)
                    .omitNullValues()
                    .reprStringValues()
                    .toString();
        }
    }
}
