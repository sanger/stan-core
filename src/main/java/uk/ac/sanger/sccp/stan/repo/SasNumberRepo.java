package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.SasNumber;
import uk.ac.sanger.sccp.stan.model.SasNumber.Status;

import javax.persistence.EntityNotFoundException;
import java.util.*;

public interface SasNumberRepo extends CrudRepository<SasNumber, Integer> {
    Optional<SasNumber> findBySasNumber(String sasNumber);

    default SasNumber getBySasNumber(String sasNumber) throws EntityNotFoundException {
        return findBySasNumber(sasNumber).orElseThrow(() -> new EntityNotFoundException("Unknown sas number: "+sasNumber));
    }

    @Query(value = "select prefix from sas_sequence", nativeQuery = true)
    List<String> getPrefixes();

    /**
     * This increments the count for a given prefix.
     * This should be called inside a transaction before {@link #getCount}. This method will lock the row for the
     * transaction so another thread cannot produce the same result.
     * Call {@link #createNumber} instead of calling this directly.
     * @param prefix the sas number prefix to increment
     */
    @Modifying
    @Query(value = "update sas_sequence set counter = (counter + 1) where prefix = ?1", nativeQuery = true)
    void _incrementCount(String prefix);

    /**
     * This gets the count for a given prefix.
     * If you're updating the counter, this should be called in a transaction and {@link #_incrementCount} should
     * be called first.
     * @param prefix the sas number prefix to get the count for
     * @return the count for the given prefix
     */
    @Query(value = "select counter from sas_sequence where prefix = ?1", nativeQuery = true)
    int getCount(String prefix);

    /**
     * Increments the count for a prefix and returns the SAS number combining the prefix with the incremented number.
     * This should be called in a transaction.
     * @param prefix the prefix for the new sas number
     * @return a new SAS number using the given prefix and the updated count for that prefix
     */
    default String createNumber(String prefix) {
        _incrementCount(prefix);
        int n = getCount(prefix);
        return prefix.toUpperCase() + n;
    }

    /**
     * Gets SAS numbers in any of the given statuses
     * @param statuses a collection of statuses
     * @return the matching SAS numbers
     */
    Iterable<SasNumber> findAllByStatusIn(Collection<Status> statuses);
}
