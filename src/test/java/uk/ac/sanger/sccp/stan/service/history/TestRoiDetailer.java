package uk.ac.sanger.sccp.stan.service.history;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.request.history.HistoryEntry;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Test {@link RoiDetailer} */
class TestRoiDetailer {

    HistoryEntry entry;
    Map<Integer, Slot> slotIdMap;
    List<Address> entryAddresses;
    RoiDetailer detailer;

    @BeforeEach
    void setUp() {
        entry = new HistoryEntry(1, null, null, 2, 10, 20, null, null);
        slotIdMap = new HashMap<>();
        entryAddresses = new ArrayList<>();
        detailer = new RoiDetailer(entry, slotIdMap, entryAddresses);
    }

    private Roi roiWith(Integer sampleId, Integer slotId) {
        return new Roi(slotId, sampleId, null, null);
    }

    private Roi roiOf(String value) {
        return new Roi(null, null, null, value);
    }

    @Test
    void testDoesApply() {
        int labwareId = entry.getDestinationLabwareId();
        int sampleId = entry.getSampleId();
        Slot relevantSlot = new Slot(10, labwareId, null, null);
        Slot irrelevantSlot = new Slot(11, labwareId+1, null, null);
        slotIdMap.put(10, relevantSlot);
        slotIdMap.put(11, irrelevantSlot);
        assertTrue(detailer.doesApply(roiWith(sampleId, 10)));
        assertFalse(detailer.doesApply(roiWith(sampleId, 11)));
        assertFalse(detailer.doesApply(roiWith(sampleId, 12)));
        assertFalse(detailer.doesApply(roiWith(sampleId+1, 10)));
    }

    @Test
    void testGroupKey() {
        String value = "myroi";
        assertEquals(value, detailer.groupKey(roiOf(value)));
    }

    @Test
    void testDescribeItem() {
        Integer sampleId = entry.getSampleId();
        Roi roi = roiOf("myroi");
        roi.setSampleId(sampleId);
        assertEquals("ROI ("+sampleId+", [ADDRESSES]): myroi", detailer.describeItem(roi));
    }

    @Test
    void testDetailWithAddresses() {
        List<Address> addresses = List.of(new Address(1,1), new Address(1,2));
        assertEquals("ROI (20, A1, A2): myroi", detailer.detailWithAddresses("ROI (20, [ADDRESSES]): myroi", addresses));
    }

    @Test
    void testDetailWithoutAddresses() {
        assertEquals("ROI (20): myroi", detailer.detailWithoutAddresses("ROI (20, [ADDRESSES]): myroi"));
    }

    @Test
    void testItemSlotId() {
        Integer slotId = 15;
        assertEquals(slotId, detailer.itemSlotId(roiWith(10, slotId)));
    }
}