package uk.ac.sanger.sccp.stan.service.operation.confirm;

import uk.ac.sanger.sccp.stan.model.Labware;
import uk.ac.sanger.sccp.stan.model.PlanOperation;
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

    public ConfirmSectionValidation(Collection<String> problems) {
        this.problems = problems;
        this.labware = null;
        this.lwPlans = null;
    }

    public ConfirmSectionValidation(UCMap<Labware> labware, Map<Integer, PlanOperation> lwPlans) {
        this.problems = List.of();
        this.labware = labware;
        this.lwPlans = lwPlans;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfirmSectionValidation that = (ConfirmSectionValidation) o;
        return (Objects.equals(this.problems, that.problems)
                && Objects.equals(this.labware, that.labware)
                && Objects.equals(this.lwPlans, that.lwPlans));
    }

    @Override
    public int hashCode() {
        return Objects.hash(problems, labware, lwPlans);
    }
}
