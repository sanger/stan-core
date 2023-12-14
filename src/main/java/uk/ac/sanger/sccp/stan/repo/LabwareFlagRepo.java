package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.LabwareFlag;

import java.util.Collection;
import java.util.List;

public interface LabwareFlagRepo extends CrudRepository<LabwareFlag, Integer> {
    @Query("select lf from LabwareFlag lf where lf.labware.id in (?1)")
    List<LabwareFlag> findAllByLabwareIdIn(Collection<Integer> labwareIds);

    List<LabwareFlag> findAllByOperationIdIn(Collection<Integer> opIds);
}
