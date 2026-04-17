package uk.ac.sanger.sccp.stan.service.history;

import org.springframework.stereotype.Service;
import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.request.history.HistoryEntry;

import java.util.List;
import java.util.Map;

/**
 * Factory for dispensing {@link Detailer}s
 * @author dr6
 */
@Service
public class DetailerFactory {
    public Detailer<Measurement> measurementDetailer(HistoryEntry entry, Map<Integer, Slot> slotIdMap, List<Address> entryAddresses) {
        return new MeasurementDetailer(entry, slotIdMap, entryAddresses);
    }

    public Detailer<OperationComment> commentDetailer(HistoryEntry entry, Map<Integer, Slot> slotIdMap, List<Address> entryAddresses) {
        return new CommentDetailer(entry, slotIdMap, entryAddresses);
    }

    public Detailer<Roi> roiDetailer(HistoryEntry entry, Map<Integer, Slot> slotIdMap, List<Address> entryAddresses) {
        return new RoiDetailer(entry, slotIdMap, entryAddresses);
    }
}
