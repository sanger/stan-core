package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.LabelType;
import uk.ac.sanger.sccp.stan.model.Printer;

import javax.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;

public interface PrinterRepo extends CrudRepository<Printer, Integer> {
    Optional<Printer> findByName(String name);

    default Printer getByName(String name) throws EntityNotFoundException {
        return findByName(name).orElseThrow(() -> new EntityNotFoundException("Printer not found: "+name));
    }

    List<Printer> findAllByLabelType(LabelType labelType);
}
