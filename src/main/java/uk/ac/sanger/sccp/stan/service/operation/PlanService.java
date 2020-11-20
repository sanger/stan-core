package uk.ac.sanger.sccp.stan.service.operation;

import uk.ac.sanger.sccp.stan.model.PlanOperation;

import java.util.List;

public interface PlanService {
    List<PlanOperation> recordPlan();
}
