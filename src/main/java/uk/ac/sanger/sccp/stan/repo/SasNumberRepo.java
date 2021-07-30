package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.SasNumber;

import javax.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;

public interface SasNumberRepo extends CrudRepository<SasNumber, Integer> {
    Optional<SasNumber> findBySasNumber(String sasNumber);

    default SasNumber getBySasNumber(String sasNumber) throws EntityNotFoundException {
        return findBySasNumber(sasNumber).orElseThrow(() -> new EntityNotFoundException("Unknown sas number: "+sasNumber));
    }

    @Query(value = "select prefix from sas_sequence", nativeQuery = true)
    List<String> getPrefixes();

    @Modifying
    @Query(value = "update sas_sequence set counter = (counter + 1) where prefix = ?1", nativeQuery = true)
    void _incrementCount(String prefix);

    @Query(value = "select counter from sas_sequence where prefix = ?1", nativeQuery = true)
    int getCount(String prefix);

    default String createNumber(String prefix) {
        _incrementCount(prefix);
        int n = getCount(prefix);
        return prefix.toUpperCase() + n;
    }
}
