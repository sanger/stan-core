package uk.ac.sanger.sccp.stan.request;

import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.model.PlanOperation;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;
import java.util.Objects;

import static uk.ac.sanger.sccp.utils.BasicUtils.newArrayList;
import static uk.ac.sanger.sccp.utils.BasicUtils.nullToEmpty;

/**
 * The data about a previously recorded plan
 * @author dr6
 */
public class PlanData {
    private PlanOperation plan;
    private List<LabwareFlagged> sources;
    private LabwareFlagged destination;
    private List<List<Address>> groups;

    public PlanData(PlanOperation plan, Iterable<LabwareFlagged> sources, LabwareFlagged destination, List<List<Address>> groups) {
        setPlan(plan);
        setSources(sources);
        setDestination(destination);
        setGroups(groups);
    }

    public PlanData() {
        this(null, null, null, null);
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

    public List<List<Address>> getGroups() {
        return this.groups;
    }

    public void setGroups(List<List<Address>> groups) {
        this.groups = nullToEmpty(groups);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanData that = (PlanData) o;
        return (Objects.equals(this.plan, that.plan)
                && Objects.equals(this.sources, that.sources)
                && Objects.equals(this.destination, that.destination)
                && Objects.equals(this.groups, that.groups)
        );
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
                .add("groups", groups)
                .toString();
    }
}
