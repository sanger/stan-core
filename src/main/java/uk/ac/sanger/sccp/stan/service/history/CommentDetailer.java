package uk.ac.sanger.sccp.stan.service.history;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.request.history.HistoryEntry;

import java.util.List;
import java.util.Map;

/**
 * @author dr6
 */
public class CommentDetailer extends Detailer<OperationComment> {
    protected CommentDetailer(HistoryEntry entry, Map<Integer, Slot> slotIdMap, List<Address> entryAddresses) {
        super(entry, slotIdMap, entryAddresses);
    }

    @Override
    protected boolean doesApply(OperationComment opcom) {
        return doesApply(opcom.getSampleId(), opcom.getLabwareId(), opcom.getSlotId());
    }

    @Override
    protected String groupKey(OperationComment opcom) {
        return opcom.getComment().getText();
    }

    @Override
    protected String describeItem(OperationComment opcom) {
        return opcom.getComment().getText();
    }

    @Override
    protected Integer itemSlotId(OperationComment opcom) {
        return opcom.getSlotId();
    }
}
