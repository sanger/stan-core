package uk.ac.sanger.sccp.stan.service.releasefile;

import uk.ac.sanger.sccp.utils.tsv.TsvColumn;

import static java.util.Objects.requireNonNull;

/**
 * A column in a release file that takes the appropriate value (if any) from the entry's tag data
 * @author dr6
 */
public class TagDataColumn implements TsvColumn<ReleaseEntry> {
    private final String heading;

    public TagDataColumn(String heading) {
        this.heading = requireNonNull(heading, "Tag column heading cannot be null.");
    }

    @Override
    public String get(ReleaseEntry entry) {
        var tagData = entry.getTagData();
        return (tagData==null ? null : tagData.get(this.heading));
    }

    @Override
    public String toString() {
        return this.heading;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TagDataColumn that = (TagDataColumn) o;
        return this.heading.equals(that.heading);
    }

    @Override
    public int hashCode() {
        return this.heading.hashCode();
    }
}
