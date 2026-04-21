package uk.ac.sanger.sccp.stan.service.history;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.request.history.HistoryEntry;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TestCommentDetailer {

    HistoryEntry entry;
    Map<Integer, Slot> slotIdMap;
    List<Address> entryAddresses;
    CommentDetailer detailer;

    @BeforeEach
    void setup() {
        entry = new HistoryEntry(1, null, null, 2, 10, 20, null, null);
        slotIdMap = new HashMap<>();
        entryAddresses = new ArrayList<>();
        detailer = new CommentDetailer(entry, slotIdMap, entryAddresses);
    }

    private OperationComment opComWith(Integer sampleId, Integer slotId, Integer labwareId) {
        return new OperationComment(null, null, null, sampleId, slotId, labwareId);
    }

    private OperationComment opComOf(String value) {
        Comment com = new Comment(1, value, "cat");
        return new OperationComment(1, com, null, null, null, null);
    }

    @Test
    void testDoesApply() {
        int labwareId = entry.getDestinationLabwareId();
        int sampleId = entry.getSampleId();
        Slot relevantSlot = new Slot(10, labwareId, null, null);
        Slot irrelevantSlot = new Slot(11, labwareId+1, null, null);
        slotIdMap.put(10, relevantSlot);
        slotIdMap.put(11, irrelevantSlot);

        assertTrue(detailer.doesApply(opComWith(sampleId, 10, null)));
        assertTrue(detailer.doesApply(opComWith(sampleId, null, labwareId)));
        assertFalse(detailer.doesApply(opComWith(sampleId, 11, null)));
        assertFalse(detailer.doesApply(opComWith(sampleId, 12, null)));
        assertFalse(detailer.doesApply(opComWith(sampleId, null, labwareId+1)));
        assertFalse(detailer.doesApply(opComWith(sampleId+1, 10, null)));
    }

    @Test
    void testGroupKey() {
        String value = "ilikepie";
        assertEquals(value, detailer.groupKey(opComOf(value)));
    }

    @Test
    void testDescribeItem() {
        String value = "ilikepie";
        assertEquals(value, detailer.describeItem(opComOf(value)));
    }

    @Test
    void testItemSlotId() {
        Integer slotId = 11;
        assertEquals(slotId, detailer.itemSlotId(opComWith(100, slotId, null)));
    }
}