package uk.ac.sanger.sccp.stan.service.releasefile;

import java.util.List;
import java.util.Objects;

/**
 * @author dr6
 */
public class ReleaseFileContent {
    private final ReleaseFileMode mode;
    private final List<ReleaseEntry> entries;

    public ReleaseFileContent(ReleaseFileMode mode, List<ReleaseEntry> entries) {
        this.mode = mode;
        this.entries = entries;
    }

    public ReleaseFileMode getMode() {
        return this.mode;
    }

    public List<ReleaseEntry> getEntries() {
        return this.entries;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReleaseFileContent that = (ReleaseFileContent) o;
        return (this.mode == that.mode
                && Objects.equals(this.entries, that.entries));
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, entries);
    }
}
