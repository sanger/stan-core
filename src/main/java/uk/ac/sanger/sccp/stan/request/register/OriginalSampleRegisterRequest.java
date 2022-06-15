package uk.ac.sanger.sccp.stan.request.register;

import java.util.List;
import java.util.Objects;

/**
 * A request to register one or more samples of tissue.
 * @author dr6
 */
public class OriginalSampleRegisterRequest {
    private List<OriginalSampleData> samples;

    public OriginalSampleRegisterRequest() {
        this(null);
    }

    public OriginalSampleRegisterRequest(List<OriginalSampleData> samples) {
        setSamples(samples);
    }

    public List<OriginalSampleData> getSamples() {
        return this.samples;
    }

    public void setSamples(List<OriginalSampleData> samples) {
        this.samples = (samples==null ? List.of() : samples);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OriginalSampleRegisterRequest that = (OriginalSampleRegisterRequest) o;
        return Objects.equals(this.samples, that.samples);
    }

    @Override
    public int hashCode() {
        return Objects.hash(samples);
    }

    @Override
    public String toString() {
        return String.format("OriginalSampleRegisterRequest(%s)", samples);
    }
}
