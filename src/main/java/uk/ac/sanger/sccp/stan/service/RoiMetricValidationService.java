package uk.ac.sanger.sccp.stan.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.RoiRepo;
import uk.ac.sanger.sccp.stan.request.SampleMetricsRequest;
import uk.ac.sanger.sccp.stan.request.SampleMetricsRequest.SampleMetric;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * Service to validate metrics in {@link SampleMetricsRequest}
 */
@Service
public class RoiMetricValidationService {
    private final RoiRepo roiRepo;

    @Autowired
    public RoiMetricValidationService(RoiRepo roiRepo) {
        this.roiRepo = roiRepo;
    }

    /**
     * Validates the requested sample metrics.
     * Checks for problems with the given metrics.
     * Returns sanitised metrics.
     * @param problems receptacle for problems
     * @param lw the labware the metrics apply to, if known
     * @param metrics the requested metrics
     * @return the sanitised metrics
     **/
    public List<SampleMetric> validateMetrics(Collection<String> problems, Labware lw, List<SampleMetric> metrics) {
        if (nullOrEmpty(metrics)) {
            problems.add("No metrics supplied.");
            return metrics;
        }
        metrics = sanitise(problems, metrics);
        if (lw!=null) {
            checkRois(problems, lw, metrics);
        }
        checkDupes(problems, metrics);
        return metrics;
    }

    /**
     * Basic sanitisation of metric strings
     * @param problems receptacle for problems
     * @param metrics the metrics to sanitise
     * @return the sanitised metrics, excluding any with missing fields
     */
    List<SampleMetric> sanitise(Collection<String> problems, List<SampleMetric> metrics) {
        // In theory we could later apply some more string validation to these fields
        boolean nameMissing = false, roiMissing = false;
        List<SampleMetric> sanitisedMetrics = new ArrayList<>(metrics.size());
        for (SampleMetric sm : metrics) {
            boolean ok = true;
            if (!sanitiseField(sm, SampleMetric::getRoi, SampleMetric::setRoi)) {
                roiMissing = true;
                ok = false;
            }
            if (!sanitiseField(sm, SampleMetric::getName, SampleMetric::setName)) {
                nameMissing = true;
                ok = false;
            }
            if (!sanitiseField(sm, SampleMetric::getValue, SampleMetric::setValue)) {
                ok = false;
            }
            if (ok) {
                sanitisedMetrics.add(sm);
            }
        }
        if (nameMissing) {
            problems.add("Name missing from metric.");
        }
        if (roiMissing) {
            problems.add("ROI missing from metric.");
        }
        return sanitisedMetrics;
    }

    /**
     * Trims an attribute of the metric and sets it to null if it is blank
     * @param sm the given metric
     * @param getter the function to get the value of the attribute from the metric
     * @param setter the function to set the value of the attribute in the metric
     * @return true if the attribute is present (non-empty, non-null) in the metric; false if it is absent
     */
    boolean sanitiseField(SampleMetric sm, Function<SampleMetric, String> getter, BiConsumer<SampleMetric, String> setter) {
        String attribute = getter.apply(sm);
        if (attribute!=null) {
            attribute = emptyToNull(attribute.trim());
            setter.accept(sm, attribute);
        }
        return attribute!=null;
    }

    /**
     * Checks for dupes in the given metrics.
     * The same roi should not have same metric (by name) specified multiple times.
     * @param problems receptacle for problems
     * @param metrics the specified metrics
     */
    void checkDupes(Collection<String> problems, List<SampleMetric> metrics) {
        Set<MetricKey> keys = new HashSet<>(metrics.size());
        Set<MetricKey> dupeMetricKeys = new LinkedHashSet<>();
        for (SampleMetric sm : metrics) {
            MetricKey key = new MetricKey(sm.getRoi(), sm.getName());
            if (!keys.add(key)) {
                dupeMetricKeys.add(key);
            }
        }
        if (!dupeMetricKeys.isEmpty()) {
            problems.add("Duplicate metrics supplied for the same roi: "+dupeMetricKeys);
        }
    }

    /**
     * Check that ROIs in the metrics match ROIs previously recorded in the labware.
     * @param problems receptacle for problems
     * @param lw the labware
     * @param metrics the requested metrics
     */
    void checkRois(Collection<String> problems, Labware lw, List<SampleMetric> metrics) {
        List<Integer> slotIds = lw.getSlots().stream().map(Slot::getId).toList();
        List<Roi> rois = roiRepo.findAllBySlotIdIn(slotIds);
        if (rois.isEmpty()) {
            problems.add("No ROIs have been recorded in labware "+lw.getBarcode()+".");
            return;
        }
        UCMap<String> validRoiNames = new UCMap<>();
        for (Roi roi : rois) {
            validRoiNames.put(roi.getRoi().toUpperCase(), roi.getRoi());
        }

        Set<String> invalidRois = new LinkedHashSet<>();
        for (SampleMetric sm : metrics) {
            String roi = sm.getRoi();
            if (nullOrEmpty(roi)) {
                continue;
            }
            String validRoi = validRoiNames.get(roi.toUpperCase());
            if (validRoi==null) {
                invalidRois.add(repr(roi));
            } else {
                sm.setRoi(validRoi); // put the name in its canonical form
            }
        }

        if (!invalidRois.isEmpty()) {
            problems.add("ROIs not present in labware "+lw.getBarcode()+": "+invalidRois);
        }
    }

    /**
     * A roi and a name, distinct case-insensitively, but keeping their original case
     */
    static class MetricKey {
        private final String roi, name;
        private final int hash;

        public MetricKey(String roi, String name) {
            this.roi = roi;
            this.name = name;
            this.hash = 31 * roi.toUpperCase().hashCode() + name.toUpperCase().hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MetricKey that = (MetricKey) o;
            return (this.hash==that.hash && this.name.equalsIgnoreCase(that.name) && this.roi.equalsIgnoreCase(that.roi));
        }

        @Override
        public int hashCode() {
            return this.hash;
        }

        @Override
        public String toString() {
            return String.format("(roi=%s, name=%s)", repr(roi), repr(name));
        }
    }
}
