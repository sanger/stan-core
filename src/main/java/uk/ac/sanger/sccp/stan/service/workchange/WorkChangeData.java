package uk.ac.sanger.sccp.stan.service.workchange;

import uk.ac.sanger.sccp.stan.model.Operation;
import uk.ac.sanger.sccp.stan.model.Work;

import java.util.List;

/**
 * Data loaded while processing a work change request
 */
public record WorkChangeData(Work work, List<Operation> ops) {
}
