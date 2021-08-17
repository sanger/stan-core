package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.Work;
import uk.ac.sanger.sccp.stan.model.Work.Status;

import javax.persistence.EntityNotFoundException;
import java.util.*;

public interface WorkRepo extends CrudRepository<Work, Integer> {
    Optional<Work> findByWorkNumber(String workNumber);

    default Work getByWorkNumber(String workNumber) throws EntityNotFoundException {
        return findByWorkNumber(workNumber).orElseThrow(() -> new EntityNotFoundException("Unknown work number: "+workNumber));
    }

    @Query(value = "select prefix from work_sequence", nativeQuery = true)
    List<String> getPrefixes();

    /**
     * This increments the count for a given prefix.
     * This should be called inside a transaction before {@link #getCount}. This method will lock the row for the
     * transaction so another thread cannot produce the same result.
     * Call {@link #createNumber} instead of calling this directly.
     * @param prefix the work prefix to increment
     */
    @Modifying
    @Query(value = "update work_sequence set counter = (counter + 1) where prefix = ?1", nativeQuery = true)
    void _incrementCount(String prefix);

    /**
     * This gets the count for a given prefix.
     * If you're updating the counter, this should be called in a transaction and {@link #_incrementCount} should
     * be called first.
     * @param prefix the work prefix to get the count for
     * @return the count for the given prefix
     */
    @Query(value = "select counter from work_sequence where prefix = ?1", nativeQuery = true)
    int getCount(String prefix);

    /**
     * Increments the count for a prefix and returns the work number combining the prefix with the incremented number.
     * This should be called in a transaction.
     * @param prefix the prefix for the new work
     * @return a new work number using the given prefix and the updated count for that prefix
     */
    default String createNumber(String prefix) {
        _incrementCount(prefix);
        int n = getCount(prefix);
        return prefix.toUpperCase() + n;
    }

    /**
     * Gets work numbers in any of the given statuses
     * @param statuses a collection of statuses
     * @return the matching works
     */
    Iterable<Work> findAllByStatusIn(Collection<Status> statuses);
}
