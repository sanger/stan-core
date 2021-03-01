package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.BarcodeSeed;

public interface BarcodeSeedRepo extends CrudRepository<BarcodeSeed, Integer> {
    String STAN = "STAN-";

    default String createBarcode(String prefix) {
        return save(new BarcodeSeed()).toBarcode(prefix);
    }

    default String createStanBarcode() {
        return createBarcode(STAN);
    }
}
