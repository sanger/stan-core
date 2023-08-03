package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.LabwareProbe;

import java.util.Collection;
import java.util.List;

public interface LabwareProbeRepo extends CrudRepository<LabwareProbe, Integer> {
    List<LabwareProbe> findAllByOperationIdIn(Collection<Integer> opIds);
}
