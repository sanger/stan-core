package uk.ac.sanger.sccp.stan.service.history;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.request.history.HistoryEntry;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Test {@link ResultDetailer} */
class TestResultDetailer {

    HistoryEntry entry;
    Map<Integer, Slot> slotIdMap;
    List<Address> entryAddresses;
    ResultDetailer detailer;

    @BeforeEach
    void setup() {
        entry = new HistoryEntry(1, null, null, 2, 10, 20, null, null);
        slotIdMap = new HashMap<>();
        entryAddresses = new ArrayList<>();
        detailer = new ResultDetailer(entry, slotIdMap, entryAddresses);
    }

    private ResultOp resultWith(Integer sampleId, Integer slotId) {
        return new ResultOp(100, PassFail.pass, null, sampleId, slotId, null);
    }

    private ResultOp resultOf(PassFail pf) {
        return new ResultOp(100, pf, null, null, null, null);
    }

    @Test
    void testDoesApply() {
        int labwareId = entry.getDestinationLabwareId();
        int sampleId = entry.getSampleId();
        Slot relevantSlot = new Slot(10, labwareId, null, null);
        Slot irrelevantSlot = new Slot(11, labwareId+1, null, null);
        slotIdMap.put(10, relevantSlot);
        slotIdMap.put(11, irrelevantSlot);

        assertTrue(detailer.doesApply(resultWith(sampleId, 10)));
        assertFalse(detailer.doesApply(resultWith(sampleId+1, 10)));
        assertFalse(detailer.doesApply(resultWith(sampleId, 11)));
        assertFalse(detailer.doesApply(resultWith(sampleId, 12)));
    }

    @Test
    void testGroupKey() {
        assertEquals(PassFail.fail, detailer.groupKey(resultOf(PassFail.fail)));
        assertEquals(PassFail.pass, detailer.groupKey(resultOf(PassFail.pass)));
    }

    @Test
    void testDescribeItem() {
        assertEquals("pass", detailer.describeItem(resultOf(PassFail.pass)));
        assertEquals("fail", detailer.describeItem(resultOf(PassFail.fail)));
    }

    @Test
    void testItemSlotId() {
        Integer slotId = 11;
        assertEquals(slotId, detailer.itemSlotId(resultWith(null, slotId)));
    }
}