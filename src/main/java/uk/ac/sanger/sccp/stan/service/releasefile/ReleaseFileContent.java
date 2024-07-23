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

    public ReleaseFileContent(Set<ReleaseFileMode> modes, List<ReleaseEntry> entries, Set<ReleaseFileOption> options) {
        this.modes = modes==null ? Set.of() : modes;
        this.entries = entries;
        this.options = options;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReleaseFileContent that = (ReleaseFileContent) o;
        return (this.modes.equals(that.modes)
                && Objects.equals(this.entries, that.entries)
                && Objects.equals(this.options, that.options)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(modes, entries, options);
    }
}
