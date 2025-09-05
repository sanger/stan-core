package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.CytassistOverview;

public interface CytassistOverviewRepo extends CrudRepository<CytassistOverview, Integer> {
    /** Deletes all rows in one statement */
    @Modifying
    @Query("delete from CytassistOverview")
    void deleteAllInBatch();
}
