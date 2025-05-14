package uk.ac.sanger.sccp.stan.service.operation.confirm;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;

/**
 * The result of validating a confirm section request
 * @author dr6
 */
public class ConfirmSectionValidation {
    private final Collection<String> problems;
    private final UCMap<Labware> labware;
    private final Map<Integer, PlanOperation> lwPlans;
    private final UCMap<SlotRegion> slotRegions;
    private final Map<Integer, Comment> comments;
    private final UCMap<Work> works;

    public ConfirmSectionValidation(Collection<String> problems) {
        this.problems = problems;
        this.labware = null;
        this.lwPlans = null;
        this.slotRegions = null;
        this.comments = null;
        this.works = null;
    }

    public ConfirmSectionValidation(UCMap<Labware> labware, Map<Integer, PlanOperation> lwPlans,
                                    UCMap<SlotRegion> slotRegions, Map<Integer, Comment> comments, UCMap<Work> works) {
        this.problems = List.of();
        this.labware = labware;
        this.lwPlans = lwPlans;
        this.slotRegions = slotRegions;
        this.comments = comments;
        this.works = works;
    }

    public Collection<String> getProblems() {
        return this.problems;
    }

    public UCMap<Labware> getLabware() {
        return this.labware;
    }

    public Map<Integer, PlanOperation> getLwPlans() {
        return this.lwPlans;
    }

    public UCMap<SlotRegion> getSlotRegions() {
        return this.slotRegions;
    }

    public Map<Integer, Comment> getComments() {
        return this.comments;
    }

    public UCMap<Work> getWorks() {
        return this.works;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfirmSectionValidation that = (ConfirmSectionValidation) o;
        return (Objects.equals(this.problems, that.problems)
                && Objects.equals(this.labware, that.labware)
                && Objects.equals(this.lwPlans, that.lwPlans)
                && Objects.equals(this.slotRegions, that.slotRegions)
                && Objects.equals(this.comments, that.comments)
                && Objects.equals(this.works, that.works)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(problems, labware);
    }
}
