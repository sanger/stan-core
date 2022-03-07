package uk.ac.sanger.sccp.stan.service.measurements;

import uk.ac.sanger.sccp.stan.model.Address;
import uk.ac.sanger.sccp.stan.model.Labware;
import uk.ac.sanger.sccp.stan.request.SlotMeasurementRequest;
import uk.ac.sanger.sccp.stan.service.sanitiser.Sanitiser;
import uk.ac.sanger.sccp.utils.UCMap;

import java.util.*;
import java.util.function.Function;

import static uk.ac.sanger.sccp.utils.BasicUtils.pluralise;

/**
 * Utility for validating slot measurements, one labware at a time, accumulating problems for them all.
 * @author dr6
 */
class SlotMeasurementValidatorImp implements SlotMeasurementValidator {
    private final Collection<String> problems;
    private final UCMap<String> sanitisedMeasurementNames;
    private final Map<String, Sanitiser<String>> valueSanitisers;
    private boolean anyNullNames, anyNullValues, anyNullAddresses;
    private final Set<String> invalidNames;

    /**
     * Constructs a new validator for validating slot measurements
     * @param validMeasurementNames the supported measurement names, in their canonical form
     */
    public SlotMeasurementValidatorImp(Collection<String> validMeasurementNames) {
        sanitisedMeasurementNames = UCMap.from(validMeasurementNames, Function.identity());
        valueSanitisers = new HashMap<>(sanitisedMeasurementNames.size());

        problems = new LinkedHashSet<>();
        invalidNames = new LinkedHashSet<>();
    }

    @Override
    public void setValueSanitiser(String measurementName, Sanitiser<String> sanitiser) {
        valueSanitisers.put(measurementName, sanitiser);
    }

    @Override
    public List<SlotMeasurementRequest> validateSlotMeasurements(Labware lw, Collection<SlotMeasurementRequest> sms) {
        List<SlotMeasurementRequest> sanitised = new ArrayList<>(sms.size());

        Set<Address> invalidAddresses = new LinkedHashSet<>();
        Set<Address> emptyAddresses = new LinkedHashSet<>();
        Set<SlotMeasurementKey> keys = new HashSet<>();
        Set<SlotMeasurementKey> dupes = new LinkedHashSet<>();

        for (SlotMeasurementRequest sm : sms) {
            SlotMeasurementRequest sanSm = sanitiseSlotMeasurement(lw, sm, invalidAddresses, emptyAddresses);
            if (sanSm!=null) {
                sanitised.add(sanSm);
                SlotMeasurementKey key = new SlotMeasurementKey(sanSm);
                if (!keys.add(key)) {
                    dupes.add(key);
                }
            }
        }
        if (lw!=null) {
            if (!invalidAddresses.isEmpty()) {
                problems.add(pluralise("Invalid slot{s} specified in measurement{s} for labware ", invalidAddresses.size())
                        + lw.getBarcode() + ": " +invalidAddresses);
            }
            if (!emptyAddresses.isEmpty()) {
                problems.add(pluralise("Empty slot{s} specified in measurement{s} for labware ", emptyAddresses.size())
                        + lw.getBarcode()+": "+emptyAddresses);
            }
        }
        if (!dupes.isEmpty()) {
            StringBuilder msg = new StringBuilder("Same measurement");
            if (dupes.size() > 1) {
                msg.append('s');
            }
            msg.append(" specified multiple times in the same slot");
            if (dupes.size() > 1) {
                msg.append('s');
            }
            if (lw!=null) {
                msg.append(" of labware ").append(lw.getBarcode());
            }
            msg.append(':');
            for (var dupe : dupes) {
                msg.append(' ').append(dupe.name).append(" in ").append(dupe.address).append(';');
            }
            msg.setLength(msg.length()-1);
            problems.add(msg.toString());
        }
        return sanitised;
    }

    /**
     * Sanitises the given measurement.
     * Checks the measurement name, value and address look suitable.
     * Adds bad addresses to the given sets.
     * Also adds problems to the instances problem fields.
     * Returns null if the given measurement cannot be sanitised.
     * @param lw the labware (if known)
     * @param sm the given information about the measurement
     * @param invalidAddresses receptacle for invalid addresses found
     * @param emptyAddresses receptacle for empty addresses given for measurements
     * @return a sanitised version of the measurement request, if it could be sanitised
     */
    public SlotMeasurementRequest sanitiseSlotMeasurement(
            Labware lw, SlotMeasurementRequest sm,
            final Set<Address> invalidAddresses, final Set<Address> emptyAddresses) {
        String name = sm.getName();
        String value = sm.getValue();
        if (sm.getName()==null || sm.getName().isEmpty()) {
            anyNullNames = true;
            name = null;
        } else {
            name = sanitiseName(name);
            if (name==null) {
                invalidNames.add(sm.getName());
            }
        }
        if (value==null || value.isEmpty()) {
            anyNullValues = true;
            value = null;
        } else {
            value = sanitiseValue(name, value);
            // If there is a problem, it will be added to problems by the sanitiser
        }
        Boolean pop = null;
        if (sm.getAddress()==null) {
            anyNullAddresses = true;
        } else if (lw !=null) {
            pop = isSlotPopulated(lw, sm.getAddress());
            if (pop==null) {
                invalidAddresses.add(sm.getAddress());
            } else if (!pop) {
                emptyAddresses.add(sm.getAddress());
            }
        }

        if (name != null && value != null && pop != null && pop) {
            return new SlotMeasurementRequest(sm.getAddress(), name, value);
        }
        return null;
    }

    /**
     * Is the slot populated, if it exists?
     * @param lw the labware
     * @param address the address of the slot
     * @return true if the slot has contents; false if it is empty; null if it does not exist
     */
    public Boolean isSlotPopulated(Labware lw, Address address) {
        if (lw==null || address==null) {
            return null;
        }
        var optSlot = lw.optSlot(address);
        if (optSlot.isEmpty()) {
            return null;
        }
        return !optSlot.get().getSamples().isEmpty();
    }

    /**
     * Gets the sanitised version of the measurement name.
     * Returns null if the given name does not correspond to an expected measurement name.
     * @param name the given measurement name
     * @return the sanitised name, if matched; null if unmatched.
     */
    public String sanitiseName(String name) {
        return sanitisedMeasurementNames.get(name);
    }

    /**
     * Gets the sanitised measurement value.
     * Returns null and saves a problem if it cannot be sanitised.
     * @param sanName the sanitised measurement name.
     * @param value the given value
     * @return the sanitised name, if sanitisation succeeded; null otherwise
     */
    public String sanitiseValue(String sanName, String value) {
        if (sanName!=null && value!=null) {
            Sanitiser<String> sanitiser = valueSanitisers.get(sanName);
            if (sanitiser!=null) {
                return sanitiser.sanitise(problems, value);
            }
        }
        return value;
    }

    @Override
    public Set<String> compileProblems() {
        Set<String> compiledProblems = new LinkedHashSet<>();
        if (anyNullNames) {
            compiledProblems.add("Measurements given without a name.");
        }
        if (anyNullValues) {
            compiledProblems.add("Measurements given without a value.");
        }
        if (anyNullAddresses) {
            compiledProblems.add("Measurements given without a slot address.");
        }
        if (!invalidNames.isEmpty()) {
            compiledProblems.add(pluralise("Unexpected measurement{s} specified for this operation: ", invalidNames.size())
                    + invalidNames);
        }
        compiledProblems.addAll(problems);
        return compiledProblems;
    }

    /**
     * Struct-like representing a name and an address of a slot measurement
     */
    static class SlotMeasurementKey {
        String name;
        Address address;
        SlotMeasurementKey(String name, Address address) {
            this.name = name;
            this.address = address;
        }

        public SlotMeasurementKey(SlotMeasurementRequest sanSm) {
            this(sanSm.getName(), sanSm.getAddress());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SlotMeasurementKey that = (SlotMeasurementKey) o;
            return (Objects.equals(this.name, that.name)
                    && Objects.equals(this.address, that.address));
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, address);
        }
    }
}
