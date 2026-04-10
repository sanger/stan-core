package uk.ac.sanger.sccp.stan.service.operation.confirm;

import uk.ac.sanger.sccp.stan.model.*;

import java.util.*;
import java.util.stream.Collectors;

import static uk.ac.sanger.sccp.utils.BasicUtils.nullOrEmpty;

/**
 * Util to provide descriptions of samples used in ops.
 * <p>Usage: <code>"You have misused sample "+describer.describe(sampleId)+"."</code>
 * <br><code>You have misused sample 40 (section 8 of EXT12) from STAN-12 (A3).</code>
 * <p>Descriptions are cached.
 * @author dr6
 */
public class SampleDescriber {
    private final Map<Integer, String> cache;
    private final Map<Integer, Labware> lwMap;
    private final Map<Integer, Sample> sampleMap;
    private final Map<Integer, Set<Slot>> sampleSlots;

    /**
     * Creates a sample describer using the given information.
     * @param lwMap map to get labware from labware id
     * @param sampleMap map to get sample from sample id
     * @param sampleSlots map from sample id to the slots the sample came from
     */
    public SampleDescriber(Map<Integer, Labware> lwMap, Map<Integer, Sample> sampleMap, Map<Integer, Set<Slot>> sampleSlots) {
        this.lwMap = lwMap;
        this.sampleMap = sampleMap;
        this.sampleSlots = sampleSlots;
        this.cache = new HashMap<>();
    }

    /**
     * Gets a description of a sample, using an internal cache.
     * @param sampleId the id of the sample to describe
     * @return a string description of the sample
     */
    public String describe(Integer sampleId) {
        return cache.computeIfAbsent(sampleId, this::makeDescription);
    }

    /**
     * Describe some basic fields of the sample
     */
    String describeSampleFields(Sample sample) {
        String xn = sample.getTissue().getExternalName();
        if (sample.getSection()==null) {
            return xn;
        }
        return "section "+sample.getSection()+" of "+xn;
    }

    /** Describe the location of the sample, if found. Otherwise returns null. */
    String describeSampleLocations(Integer sampleId) {
        Set<Slot> slots = sampleSlots.get(sampleId);
        if (nullOrEmpty(slots)) {
            return null;
        }
        Map<Integer, Set<Slot>> lwIdSlots = new LinkedHashMap<>();
        for (Slot slot : slots) {
            lwIdSlots.computeIfAbsent(slot.getLabwareId(), k -> new HashSet<>()).add(slot);
        }
        Set<String> locStrings = new LinkedHashSet<>();
        for (Map.Entry<Integer, Set<Slot>> entry : lwIdSlots.entrySet()) {
            Labware lw = lwMap.get(entry.getKey());
            if (lw==null) {
                continue;
            }
            if (lw.getLabwareType().getNumColumns()==1 && lw.getLabwareType().getNumRows()==1) {
                locStrings.add(lw.getBarcode());
            } else {
                String addresses = entry.getValue().stream().map(Slot::getAddress).sorted().map(Object::toString).collect(Collectors.joining(","));
                locStrings.add(lw.getBarcode()+" ("+addresses+")");
            }
        }
        if (locStrings.isEmpty()) {
            return null;
        }
        return String.join(", ", locStrings);
    }

    /** Creates a description from the sample fields and the known locations, if any */
    String makeDescription(Integer sampleId) {
        Sample sample = sampleMap.get(sampleId);
        if (sample==null) {
            return "id "+sampleId;
        }
        String samDesc = describeSampleFields(sample);
        String locs = describeSampleLocations(sampleId);
        if (locs==null) {
            return String.format("id %s (%s)", sampleId, samDesc);
        }
        return String.format("id %s (%s) from %s", sampleId, samDesc, locs);
    }
}
