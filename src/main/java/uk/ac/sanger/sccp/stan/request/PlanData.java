package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.PlanOperation;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.newArrayList;

/**
 * The data about a previously recorded plan
 * @author dr6
 */
public class PlanData {
    private PlanOperation plan;
    private List<LabwareFlagged> sources;
    private LabwareFlagged destination;

    public PlanData(PlanOperation plan, Iterable<LabwareFlagged> sources, LabwareFlagged destination) {
        setPlan(plan);
        setSources(sources);
        setDestination(destination);
    }

    public PlanData() {
        this(null, null, null);
    }

    public PlanOperation getPlan() {
        return this.plan;
    }

    public void setPlan(PlanOperation plan) {
        this.plan = plan;
    }

    public List<LabwareFlagged> getSources() {
        return this.sources;
    }

    public void setSources(Iterable<LabwareFlagged> sources) {
        this.sources = newArrayList(sources);
    }

    public LabwareFlagged getDestination() {
        return this.destination;
    }

    public void setDestination(LabwareFlagged destination) {
        this.destination = destination;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanData that = (PlanData) o;
        return (Objects.equals(this.plan, that.plan)
                && Objects.equals(this.sources, that.sources)
                && Objects.equals(this.destination, that.destination));
    }

    @Override
    public int hashCode() {
        return (destination==null ? 0 : destination.hashCode());
    }

    @Override
    public String toString() {
        return BasicUtils.describe("PlanData")
                .add("plan", plan)
                .add("sources", sources)
                .add("destination", destination)
                .toString();
    }
}
