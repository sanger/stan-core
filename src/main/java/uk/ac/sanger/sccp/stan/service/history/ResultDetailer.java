package uk.ac.sanger.sccp.stan.service.history;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.request.history.HistoryEntry;

import java.util.List;
import java.util.Map;

/**
 * Detailer for pass/fail results
 * @author dr6
 */
public class ResultDetailer extends Detailer<ResultOp> {
    protected ResultDetailer(HistoryEntry entry, Map<Integer, Slot> slotIdMap, List<Address> entryAddresses) {
        super(entry, slotIdMap, entryAddresses);
    }

    @Override
    protected boolean doesApply(ResultOp item) {
        return doesApply(item.getSampleId(), null, item.getSlotId());
    }

    @Override
    protected Object groupKey(ResultOp item) {
        return item.getResult();
    }

    @Override
    protected String describeItem(ResultOp item) {
        return item.getResult().name();
    }

    @Override
    protected Integer itemSlotId(ResultOp item) {
        return item.getSlotId();
    }
}
