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
    private final List<LabelContent> contents;

    public LabwareLabelData(String barcode, String medium, List<LabelContent> contents) {
        this.barcode = barcode;
        this.medium = medium;
        this.contents = List.copyOf(contents);
    }

    public String getBarcode() {
        return this.barcode;
    }

    public String getMedium() {
        return this.medium;
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
                .add("contents", contents)
                .toString();
    }

    public Map<String, String> getFields() {
        HashMap<String, String> fields = new HashMap<>(2 + 4 * contents.size());
        fields.put("barcode", getBarcode());
        fields.put("medium", getMedium());
        int index = 0;
        for (LabelContent content : contents) {
            addField(fields, "donor", index, content.getDonorName());
            addField(fields, "tissue", index, content.getTissueDesc());
            if (content.getReplicate()!=null) {
                addField(fields, "replicate", index, "R:"+content.getReplicate());
            }
            if (content.getSection()!=null) {
                addField(fields, "section", index, String.format("S%03d", content.getSection()));
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
        private final Integer replicate;
        private final Integer section;

        public LabelContent(String donorName, String tissueDesc, Integer replicate, Integer section) {
            this.donorName = donorName;
            this.tissueDesc = tissueDesc;
            this.replicate = replicate;
            this.section = section;
        }

        public String getDonorName() {
            return this.donorName;
        }

        public String getTissueDesc() {
            return this.tissueDesc;
        }

        public Integer getReplicate() {
            return this.replicate;
        }

        public Integer getSection() {
            return this.section;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LabelContent that = (LabelContent) o;
            return (Objects.equals(this.donorName, that.donorName)
                    && Objects.equals(this.tissueDesc, that.tissueDesc)
                    && Objects.equals(this.replicate, that.replicate)
                    && Objects.equals(this.section, that.section));
        }

        @Override
        public int hashCode() {
            return Objects.hash(donorName, tissueDesc, replicate, section);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("donorName", donorName)
                    .add("tissueDesc", tissueDesc)
                    .add("replicate", replicate)
                    .add("section", section)
                    .toString();
        }
    }
}
