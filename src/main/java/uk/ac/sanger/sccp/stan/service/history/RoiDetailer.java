package uk.ac.sanger.sccp.stan.service.history;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.request.history.HistoryEntry;

import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.joining;

/**
 * @author dr6
 */
public class RoiDetailer extends Detailer<Roi> {
    protected RoiDetailer(HistoryEntry entry, Map<Integer, Slot> slotIdMap, List<Address> entryAddresses) {
        super(entry, slotIdMap, entryAddresses);
    }

    @Override
    protected boolean doesApply(Roi roi) {
        return doesApply(roi.getSampleId(), null, roi.getSlotId());
    }

    @Override
    protected Object groupKey(Roi roi) {
        return roi.getRoi();
    }

    @Override
    protected String describeItem(Roi roi) {
        return String.format("ROI (%s, [ADDRESSES]): %s", roi.getSampleId(), roi.getRoi());
    }

    @Override
    protected String detailWithAddresses(String detail, List<Address> addresses) {
        String addressString = addresses.stream().map(Address::toString).collect(joining(", "));
        return detail.replace("[ADDRESSES]", addressString);
    }

    @Override
    protected String detailWithoutAddresses(String detail) {
        return detail.replace(", [ADDRESSES]", "");
    }

    @Override
    protected Integer itemSlotId(Roi roi) {
        return roi.getSlotId();
    }
}
