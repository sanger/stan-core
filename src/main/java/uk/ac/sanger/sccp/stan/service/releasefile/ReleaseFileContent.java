package uk.ac.sanger.sccp.stan.service.releasefile;

import uk.ac.sanger.sccp.stan.model.ReleaseFileOption;

import java.util.*;

/**
 * @author dr6
 */
public class ReleaseFileContent {
    private final Set<ReleaseFileMode> modes;
    private final List<ReleaseEntry> entries;
    private final Set<ReleaseFileOption> options;
    private final List<List<ReleaseEntry>> rows;

    public ReleaseFileContent(Set<ReleaseFileMode> modes, List<ReleaseEntry> entries, Set<ReleaseFileOption> options,
                              List<List<ReleaseEntry>> rows) {
        this.modes = modes==null ? Set.of() : modes;
        this.entries = entries;
        this.options = options;
        this.rows = rows;
    }

    public Set<ReleaseFileMode> getModes() {
        return this.modes;
    }

    public List<ReleaseEntry> getEntries() {
        return this.entries;
    }

    public Set<ReleaseFileOption> getOptions() {
        return this.options;
    }

    public List<List<ReleaseEntry>> getRows() {
        return this.rows;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReleaseFileContent that = (ReleaseFileContent) o;
        return (this.modes.equals(that.modes)
                && Objects.equals(this.entries, that.entries)
                && Objects.equals(this.options, that.options)
                && Objects.equals(this.rows, that.rows)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(modes, entries, options, rows);
    }
}
