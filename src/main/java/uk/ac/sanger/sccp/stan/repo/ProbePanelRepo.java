package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.ProbePanel;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProbePanelRepo extends CrudRepository<ProbePanel, Integer> {
    Optional<ProbePanel> findByName(String name);

    List<ProbePanel> findAllByNameIn(Collection<String> names);

    Iterable<ProbePanel> findAllByEnabled(boolean enabled);
}
