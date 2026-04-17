package uk.ac.sanger.sccp.stan.service.history;

import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.model.Slot;
import uk.ac.sanger.sccp.stan.request.history.HistoryEntry;

import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;

/**
 * Tool to combine details in history to concise strings
 * @param <I> type of detail item
 */
public abstract class Detailer<I> {
    protected final HistoryEntry entry;
    protected final Map<Integer, Slot> slotIdMap;
    protected final List<Address> entryAddresses;

    protected Detailer(HistoryEntry entry, Map<Integer, Slot> slotIdMap, List<Address> entryAddresses) {
        this.entry = entry;
        this.slotIdMap = slotIdMap;
        this.entryAddresses = entryAddresses;
    }

    /** Is the item relevant to the history entry in question? */
    protected abstract boolean doesApply(I item);
    /** Key for grouping similar items */
    protected abstract Object groupKey(I item);
    /** A string describing the item */
    protected abstract String describeItem(I item);
    /** The slot id for the item */
    protected abstract Integer itemSlotId(I item);

    /**
     * Helper method: are the given sample id, labware id and slot id suitable for the history entry?
     */
    protected boolean doesApply(Integer sampleId, Integer labwareId, Integer slotId) {
        if (sampleId != null && !sampleId.equals(entry.getSampleId())) {
            return false;
        }
        if (labwareId != null && !labwareId.equals(entry.getDestinationLabwareId())) {
            return false;
        }
        if (labwareId == null && slotId != null) {
            Slot slot = slotIdMap.get(slotId);
            //noinspection RedundantIfStatement
            if (slot == null || !slot.getLabwareId().equals(entry.getDestinationLabwareId())) {
                return false;
            }
        }
        return true;
    }

    /** Creates a description for the given group of items */
    protected String describeGroup(List<I> group) {
        String detail = describeItem(group.getFirst());
        if (group.stream().allMatch(item -> itemSlotId(item)!=null)) {
            List<Address> addresses = group.stream()
                    .map(item -> slotIdMap.get(itemSlotId(item)).getAddress())
                    .sorted()
                    .distinct()
                    .toList();
            if (!addresses.equals(entryAddresses)) {
                return detailWithAddresses(detail, addresses);
            }
        }
        return detailWithoutAddresses(detail);
    }

    /** Add the addresses into a detail string */
    protected String detailWithAddresses(String detail, List<Address> addresses) {
        String addressString = addresses.stream().map(Address::toString).collect(joining(", "));
        return addressString + ": " + detail;
    }

    /** A detail string without addresses in it */
    protected String detailWithoutAddresses(String detail) {
        return detail;
    }

    /** Groups the items and adds details for each group to the history entry */
    public void addDetails(List<I> items) {
        Map<?, List<I>> groups = items.stream()
                .filter(this::doesApply)
                .collect(groupingBy(this::groupKey));
        for (List<I> group : groups.values()) {
            final String detail = describeGroup(group);
            entry.addDetail(detail);
        }
    }
}
