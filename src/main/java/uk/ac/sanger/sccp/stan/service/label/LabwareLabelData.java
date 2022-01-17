package uk.ac.sanger.sccp.stan.service.label;

import com.google.common.base.MoreObjects;

import java.util.*;

/**
 * A collection of information that may be printed onto a labware label.
 * @author dr6
 */
public class LabwareLabelData {
    private final String barcode;
    private final String medium;
    private final String date;

    private final List<LabelContent> contents;

    public LabwareLabelData(String barcode, String medium, String date, List<LabelContent> contents) {
        this.barcode = barcode;
        this.medium = medium;
        this.date = date;
        this.contents = List.copyOf(contents);
    }

    public String getBarcode() {
        return this.barcode;
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
        return MoreObjects.toStringHelper(this)
                .add("barcode", barcode)
                .add("medium", medium)
                .add("date", date)
                .add("contents", contents)
                .toString();
    }

    public Map<String, String> getFields() {
        HashMap<String, String> fields = new HashMap<>(3 + 4 * contents.size());
        fields.put("barcode", getBarcode());
        fields.put("medium", getMedium());
        fields.put("date", getDate());
        int index = 0;
        for (LabelContent content : contents) {
            addField(fields, "donor", index, content.getDonorName());
            addField(fields, "tissue", index, content.getTissueDesc());
            if (content.getReplicate()!=null) {
                addField(fields, "replicate", index, "R:"+content.getReplicate());
            }
            if (content.getStateDesc()!=null) {
                addField(fields, "state", index, content.getStateDesc());
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

    public static class LabelContent {
        private final String donorName;
        private final String tissueDesc;
        private final String replicate;
        private final String stateDesc;

        public LabelContent(String donorName, String tissueDesc, String replicate) {
            this(donorName, tissueDesc, replicate, (String) null);
        }


        public LabelContent(String donorName, String tissueDesc, String replicate, Integer section) {
            this(donorName, tissueDesc, replicate, section==null ? null : String.format("S%03d", section));
        }

        public LabelContent(String donorName, String tissueDesc, String replicate, String stateDesc) {
            this.donorName = donorName;
            this.tissueDesc = tissueDesc;
            this.replicate = replicate;
            this.stateDesc = stateDesc;
        }

        public String getDonorName() {
            return this.donorName;
        }

        public String getTissueDesc() {
            return this.tissueDesc;
        }

        public String getReplicate() {
            return this.replicate;
        }

        public String getStateDesc() {
            return this.stateDesc;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LabelContent that = (LabelContent) o;
            return (Objects.equals(this.donorName, that.donorName)
                    && Objects.equals(this.tissueDesc, that.tissueDesc)
                    && Objects.equals(this.replicate, that.replicate)
                    && Objects.equals(this.stateDesc, that.stateDesc));
        }

        @Override
        public int hashCode() {
            return Objects.hash(donorName, tissueDesc, replicate, stateDesc);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("donorName", donorName)
                    .add("tissueDesc", tissueDesc)
                    .add("replicate", replicate)
                    .add("stateDesc", stateDesc)
                    .toString();
        }
    }
}
