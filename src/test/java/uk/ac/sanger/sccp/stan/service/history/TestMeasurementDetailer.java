package uk.ac.sanger.sccp.stan.service.history;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.request.history.HistoryEntry;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/** Test {@link MeasurementDetailer} */
class TestMeasurementDetailer {
    HistoryEntry entry;
    Map<Integer, Slot> slotIdMap;
    List<Address> entryAddresses;
    MeasurementDetailer detailer;

    @BeforeEach
    void setup() {
        entry = new HistoryEntry(1, null, null, 2, 10, 20, null, null);
        slotIdMap = new HashMap<>();
        entryAddresses = new ArrayList<>();
        detailer = new MeasurementDetailer(entry, slotIdMap, entryAddresses);
    }

    private Measurement measurementWith(Integer sampleId, Integer slotId) {
        return new Measurement(null, null, null, sampleId, null, slotId);
    }

    private Measurement measurementOf(String name, String value) {
        return new Measurement(null, name, value, null, null, null);
    }

    @Test
    void testDoesApply() {
        int labwareId = entry.getDestinationLabwareId();
        int sampleId = entry.getSampleId();
        Slot relevantSlot = new Slot(10, labwareId, null, null);
        Slot irrelevantSlot = new Slot(11, labwareId+1, null, null);
        slotIdMap.put(10, relevantSlot);
        slotIdMap.put(11, irrelevantSlot);
        assertTrue(detailer.doesApply(measurementWith(sampleId, 10)));
        assertFalse(detailer.doesApply(measurementWith(sampleId, 11)));
        assertFalse(detailer.doesApply(measurementWith(sampleId, 12)));
        assertFalse(detailer.doesApply(measurementWith(sampleId+1, 10)));
    }

    @Test
    void testGroupKey() {
        Measurement measurement = measurementOf("MKEY", "MValue");
        assertEquals(List.of("MKEY", "MValue"), detailer.groupKey(measurement));
    }

    @ParameterizedTest
    @CsvSource({
            "MKEY, MValue, MKEY: MValue",
            "Eosin, 300, Eosin: 5\u00a0min",
            "DV200 thing, 75, DV200 thing: 75\u00a0%",
            "Average size, 53, Average size: 53\u00a0bp",
    })
    void testDescribeItem(String name, String value, String expected) {
        assertEquals(expected, detailer.describeItem(measurementOf(name, value)));
    }

    @Test
    void testItemSlotId() {
        Measurement measurement = measurementWith(10, 20);
        assertEquals(20, detailer.itemSlotId(measurement));
    }

    @ParameterizedTest
    @CsvSource({
            "15, 15 sec",
            "83, 1 min 23 sec",
            "120, 2 min",
            "7000, 1 hour 56 min 40 sec",
    })
    void testDescribeSeconds(String value, String expected) {
        String desc = MeasurementDetailer.describeSeconds(value);
        assertEquals(expected, desc.replace('\u00a0', ' '));
    }

    @Test
    void testAddDetails() {
        final int labwareId = entry.getDestinationLabwareId();
        final int sampleId = entry.getSampleId();
        final Address A1 = new Address(1,1), A2=new Address(1,2);
        entryAddresses.addAll(List.of(A1, A2));
        List<Slot> slots = List.of(
                new Slot(1, labwareId, A1, null),
                new Slot(2, labwareId+1, A1, null),
                new Slot(3, labwareId, A2, null)
        );
        slots.forEach(slot -> slotIdMap.put(slot.getId(), slot));
        List<Measurement> measurements = List.of(
                new Measurement(1, "Alpha", "Alabama", sampleId, null, 3),
                new Measurement(2, "Alpha", "Alabama", sampleId, null, 2),
                new Measurement(3, "Alpha", "Alabama", sampleId, null, 1),
                new Measurement(4, "Alpha", "Alaska", sampleId, null, 1),
                new Measurement(5, "Beta", "Banana", sampleId, null, 3),
                new Measurement(7, "Gamma", "Grapefruit", sampleId+1, null, 1)
        );
        detailer.addDetails(measurements);
        assertThat(entry.getDetails()).containsExactlyInAnyOrder(
                "Alpha: Alabama", "A1: Alpha: Alaska", "A2: Beta: Banana"
        );
    }
}