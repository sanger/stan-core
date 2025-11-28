package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.SlotGroup;

import java.util.List;

public interface SlotGroupRepo extends CrudRepository<SlotGroup, Integer> {
    List<SlotGroup> findByPlanId(Integer planId);
}
