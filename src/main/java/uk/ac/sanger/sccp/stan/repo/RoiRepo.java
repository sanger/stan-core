package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Roi;
import uk.ac.sanger.sccp.stan.model.SlotIdSampleIdOpId;

import java.util.Collection;
import java.util.List;

public interface RoiRepo extends CrudRepository<Roi, SlotIdSampleIdOpId> {
    List<Roi> findAllByOperationIdIn(Collection<Integer> opId);
}
