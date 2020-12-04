package uk.ac.sanger.sccp.stan.service.operation.plan;

import java.util.Collection;

/**
 * @author dr6
 */
public interface PlanValidation {
    /**
     * Lists the problems found
     * @return a collection of problems found
     */
    Collection<String> validate();
}
