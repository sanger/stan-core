package uk.ac.sanger.sccp.stan.service.history;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.request.history.HistoryEntry;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author dr6
 */
public class MeasurementDetailer extends Detailer<Measurement> {
    static final Pattern SIZE_BP_PTN = Pattern.compile("^(Average|Main peak) size$", Pattern.CASE_INSENSITIVE);

    protected MeasurementDetailer(HistoryEntry entry, Map<Integer, Slot> slotIdMap, List<Address> entryAddresses) {
        super(entry, slotIdMap, entryAddresses);
    }

    @Override
    protected boolean doesApply(Measurement measurement) {
        return doesApply(measurement.getSampleId(), null, measurement.getSlotId());
    }

    @Override
    protected List<String> groupKey(Measurement measurement) {
        return List.of(measurement.getName(), measurement.getValue());
    }

    @Override
    protected String describeItem(Measurement measurement) {
        final String name = measurement.getName();
        final String value = measurement.getValue();
        MeasurementType mt = MeasurementType.forName(name);
        if (mt==null && BasicUtils.startsWithIgnoreCase(name, "DV200")) {
            mt = MeasurementType.DV200;
        } else if (mt==null && SIZE_BP_PTN.matcher(name).matches()) {
            mt = MeasurementType.Size_bp;
        }
        MeasurementValueType vt = (mt==null ? null : mt.getValueType());
        String detail = name + ": ";
        if (vt==MeasurementValueType.TIME) {
            detail += describeSeconds(value);
        } else {
            detail += value;
            if (mt!=null && mt.getUnit()!=null) {
                detail += "\u00a0" + mt.getUnit();
            }
        }
        return detail;
    }

    @Override
    protected Integer itemSlotId(Measurement item) {
        return item.getSlotId();
    }

    public static String describeSeconds(String value) {
        int seconds;
        try {
            seconds = Integer.parseInt(value);
        } catch (NumberFormatException nfe) {
            return value;
        }
        int minutes = seconds/60;
        if (minutes==0) {
            return seconds+"\u00a0sec";
        }
        seconds %= 60;
        int hours = minutes/60;
        if (hours==0) {
            if (seconds==0) {
                return minutes + "\u00a0min";
            }
            return minutes + "\u00a0min " + seconds + "\u00a0sec";
        }
        minutes %= 60;
        if (minutes==0 && seconds==0) {
            return hours + "\u00a0hour";
        }
        if (seconds==0) {
            return hours + "\u00a0hour " + minutes + "\u00a0min";
        }
        return String.format("%d\u00a0hour %d\u00a0min %d\u00a0sec", hours, minutes, seconds);
    }
}
