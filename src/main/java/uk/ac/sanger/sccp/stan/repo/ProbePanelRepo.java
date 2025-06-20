package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.ProbePanel;
import uk.ac.sanger.sccp.stan.model.ProbePanel.ProbeType;

import javax.persistence.EntityNotFoundException;
import java.util.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

public interface ProbePanelRepo extends CrudRepository<ProbePanel, Integer> {
    Optional<ProbePanel> findByName(String name); // TODO remove

    boolean existsByTypeAndName(ProbeType type, String name);

    Optional<ProbePanel> findByTypeAndName(ProbeType type, String name);

    default ProbePanel getByTypeAndName(ProbeType type, String name) throws EntityNotFoundException {
        return findByTypeAndName(type, name).orElseThrow(() -> new EntityNotFoundException(
                String.format("Unknown probe panel: (%s, %s)", type, repr(name))));
    }

    List<ProbePanel> findAllByTypeAndNameIn(ProbeType type, Collection<String> names);

    List<ProbePanel> findAllByType(ProbeType type);

    List<ProbePanel> findAllByTypeAndEnabled(ProbeType type, boolean enabled);
}
