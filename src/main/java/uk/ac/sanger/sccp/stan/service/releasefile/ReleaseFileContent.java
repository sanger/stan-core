package uk.ac.sanger.sccp.stan.service.releasefile;

import uk.ac.sanger.sccp.stan.model.ReleaseFileOption;

import java.util.*;

/**
 * @author dr6
 */
public class ReleaseFileContent {
    private final ReleaseFileMode mode;
    private final List<ReleaseEntry> entries;
    private final Set<ReleaseFileOption> options;

    public ReleaseFileContent(ReleaseFileMode mode, List<ReleaseEntry> entries, Set<ReleaseFileOption> options) {
        this.mode = mode;
        this.entries = entries;
        this.options = options;
    }

    public ReleaseFileMode getMode() {
        return this.mode;
    }

    public List<ReleaseEntry> getEntries() {
        return this.entries;
    }

    public Set<ReleaseFileOption> getOptions() {
        return this.options;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReleaseFileContent that = (ReleaseFileContent) o;
        return (this.mode == that.mode
                && Objects.equals(this.entries, that.entries)
                && Objects.equals(this.options, that.options)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, entries, options);
    }
}
