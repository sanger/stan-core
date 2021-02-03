package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.Release;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.newArrayList;

/**
 * @author dr6
 */
public class ReleaseResult {
    private List<Release> releases;

    public ReleaseResult() {
        this(null);
    }
    public ReleaseResult(Iterable<Release> releases) {
        setReleases(releases);
    }

    public List<Release> getReleases() {
        return this.releases;
    }

    public void setReleases(Iterable<Release> releases) {
        this.releases = newArrayList(releases);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReleaseResult that = (ReleaseResult) o;
        return Objects.equals(this.releases, that.releases);
    }

    @Override
    public int hashCode() {
        return Objects.hash(releases);
    }

    @Override
    public String toString() {
        return String.format("ReleaseResult(%s)", releases);
    }
}
