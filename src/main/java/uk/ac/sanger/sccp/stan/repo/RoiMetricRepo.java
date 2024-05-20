package uk.ac.sanger.sccp.stan.repo;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import uk.ac.sanger.sccp.stan.model.RoiMetric;

import java.time.LocalDateTime;
import java.util.Collection;

public interface RoiMetricRepo extends CrudRepository<RoiMetric, Integer> {
    /**
     * Deprecated any undeprecated metrics matching the given criteria
     * @param labwareId the labware id whose metrics to deprecate
     * @param rois the roi names whose metrics to deprecate
     * @param timestamp the timestamp to use for deprecation
     */
    @Modifying
    @Query("update RoiMetric set deprecated=?3 where labwareId=?1 and roi in (?2) and deprecated is null")
    void deprecateMetrics(Integer labwareId, Collection<String> rois, LocalDateTime timestamp);
}
