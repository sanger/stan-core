package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.Work;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.nullToEmpty;

/**
 * The response to a suggested work query.
 * @author dr6
 */
public class SuggestedWorkResponse {
    private List<SuggestedWork> suggestedWorks;
    private List<Work> works;

    public SuggestedWorkResponse(List<SuggestedWork> suggestedWorks, List<Work> works) {
        setSuggestedWorks(suggestedWorks);
        setWorks(works);
    }

    public SuggestedWorkResponse() {
        this(null, null);
    }

    /**
     * The work numbers for each barcode. Barcodes without suggested work will be omitted.
     */
    public List<SuggestedWork> getSuggestedWorks() {
        return this.suggestedWorks;
    }

    public void setSuggestedWorks(List<SuggestedWork> suggestedWorks) {
        this.suggestedWorks = nullToEmpty(suggestedWorks);
    }

    /**
     * The works indicated.
     */
    public List<Work> getWorks() {
        return this.works;
    }

    public void setWorks(List<Work> works) {
        this.works = nullToEmpty(works);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SuggestedWorkResponse that = (SuggestedWorkResponse) o;
        return (this.suggestedWorks.equals(that.suggestedWorks)
                && this.works.equals(that.works));
    }

    @Override
    public int hashCode() {
        return Objects.hash(suggestedWorks, works);
    }
}