package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.OpPanel;

import java.util.Collection;
import java.util.List;

/**
 * @author dr6
 */
public interface OpPanelRepo extends CrudRepository<OpPanel, Integer> {
    List<OpPanel> findAllByOperationId(Integer opId);
    List<OpPanel> findAllByOperationIdIn(Collection<Integer> opIds);
}
