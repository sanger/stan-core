package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.ProteinPanel;

import java.util.List;
import java.util.Optional;

public interface ProteinPanelRepo extends CrudRepository<ProteinPanel, Integer> {
    Optional<ProteinPanel> findByName(String string);

    List<ProteinPanel> findAllByEnabled(boolean enabled);
}
