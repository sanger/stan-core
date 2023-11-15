package uk.ac.sanger.sccp.stan.service;

import uk.ac.sanger.sccp.stan.model.*;
import uk.ac.sanger.sccp.stan.repo.LabwareRepo;
import uk.ac.sanger.sccp.utils.BasicUtils;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static uk.ac.sanger.sccp.utils.BasicUtils.toLinkedHashSet;

/**
 * Utility for validating labware
 * @author dr6
 */
public class LabwareValidator {
    private Collection<Labware> labware;
    private final Collection<String> errors = new LinkedHashSet<>();
    private Collection<String> givenBarcodes;
    private boolean uniqueRequired = true;
    private boolean singleSample = false;
    private boolean usedAllowed = false;
    private boolean oneFilledSlotRequired = false;

    /**
     * Creates a new validator with no labware specified
     */
    public LabwareValidator() {}

    /**
     * Creates a new validator to validate the given labware
     * @param labware the labware to validate
     */
    public LabwareValidator(Collection<Labware> labware) {
        this.labware = labware;
    }

    /**
     * Sets the labware to be validated by this validator
     * @param labware the labware to validate
     */
    public void setLabware(Collection<Labware> labware) {
        this.labware = labware;
    }

    /**
     * Gets the labware validated by this validator.
     * @return the labware
     */
    public Collection<Labware> getLabware() {
        return this.labware;
    }

    /**
     * Are labware required to be unique.
     * If true, that indicates that it is appropriate to call {@link #validateUnique}.
     * By default, this field is true.
     * @return whether labware must be unique
     */
    public boolean isUniqueRequired() {
        return this.uniqueRequired;
    }

    /**
     * Sets whether labware are required to be unique.
     * If it is set to true, that indicates that it is appropriate to call {@link #validateUnique}.
     * @param uniqueRequired whether labware must be unique
     */
    public void setUniqueRequired(boolean uniqueRequired) {
        this.uniqueRequired = uniqueRequired;
    }

    /**
     * Should labware be single-sample?
     * By default, this field is false, indicating that single-sample is not a requirement for labware.
     * @return whether single-sample labware is required
     */
    public boolean isSingleSample() {
        return this.singleSample;
    }

    /**
     * Are used labware allowed? By default, they are not allowed.
     * @return whether used labware are allowed
     */
    public boolean isUsedAllowed() {
        return this.usedAllowed;
    }

    /**
     * Is labware required to have precisely one nonempty slot?
     * By default, this field is false, indicating that the labware may have multiple filled slots (or none, untypically).
     * @return true if the labware is required to have precisely one nonempty slot
     */
    public boolean isOneFilledSlotRequired() {
        return this.oneFilledSlotRequired;
    }

    /**
     * Sets whether used labware are allowed. By default they are not allowed.
     * @param usedAllowed whether to allow used labware
     */
    public void setUsedAllowed(boolean usedAllowed) {
        this.usedAllowed = usedAllowed;
    }

    /**
     * Sets whether single-sample labware is required
     * If it is set to true, that indicates that is appropriate to call {@link #validateSingleSample}.
     * @param singleSample whether single-sample labware is required
     */
    public void setSingleSample(boolean singleSample) {
        this.singleSample = singleSample;
    }

    /**
     * Sets whether the labware is required to have precisely one sample-containing slot.
     * @param oneFilledSlotRequired whether one filled slot is required
     */
    public void setOneFilledSlotRequired(boolean oneFilledSlotRequired) {
        this.oneFilledSlotRequired = oneFilledSlotRequired;
    }

    /**
     * Adds an error message.
     * @param message an error message
     */
    public void addError(String message) {
        this.errors.add(message);
    }

    /**
     * Adds an formatted error message using a formatting string and arguments.
     * @param format the formatting string
     * @param args the arguments
     * @see String#format
     */
    public void addError(String format, Object... args) {
        addError(String.format(format, args));
    }

    /**
     * Gets the errors found by this validation.
     * @return a collection of errors found.
     */
    public Collection<String> getErrors() {
        return this.errors;
    }

    /**
     * Gets an error message made by combining all the {@link #getErrors errors} found.
     * @return a combined error message
     */
    public String combinedErrorMessage() {
        return String.join(" ", getErrors());
    }


    /**
     * Throws an exception if any errors have been found.
     * Does nothing if no errors have been found.
     * @param exceptionFunction exception creator
     * @param <E> the type of exception to throw
     * @exception E an exception describing the errors found
     * @see #combinedErrorMessage
     */
    public <E extends Exception> void throwError(Function<String, E> exceptionFunction) throws E {
        if (!errors.isEmpty()) {
            throw exceptionFunction.apply(combinedErrorMessage());
        }
    }

    /**
     * Loads the labware from the given barcodes.
     * The labware are stored inside this validator object for validation.
     * If any barcodes are unmatched, an error will be added.
     * @param lwRepo the repo to look up labware
     * @param barcodes the barcodes to look up
     * @return the found labware
     */
    public List<Labware> loadLabware(LabwareRepo lwRepo, Collection<String> barcodes) {
        givenBarcodes = barcodes;
        List<Labware> labware = lwRepo.findByBarcodeIn(barcodes);
        this.labware = labware;
        if (labware.size() < barcodes.size()) {
            Set<String> foundBarcodes = labware.stream()
                    .map(lw -> lw.getBarcode().toUpperCase())
                    .collect(Collectors.toSet());
            Set<String> missing = barcodes.stream()
                    .filter(bc -> bc == null || !foundBarcodes.contains(bc.toUpperCase()))
                    .map(BasicUtils::repr)
                    .collect(toLinkedHashSet());
            if (!missing.isEmpty()) {
                addError(BasicUtils.pluralise("Invalid labware barcode{s}: %s.", missing.size()), missing);
            }
        }
        return labware;
    }

    /**
     * Validates the labware for use as sources.
     * Checks:
     * <ul>
     *     <li>{@link #validateUnique}—if {@link #isUniqueRequired}</li>
     *     <li>{@link #validateNonEmpty}</li>
     *     <li>{@link #validateSingleSample}—if {@link #isSingleSample}</li>
     *     <li>{@link #validateOneFilledSlot}—if {@link #isOneFilledSlotRequired} and not {@code isSingleSample}</li>
     *     <li>{@link #validateStates}</li>
     * </ul>
     */
    public void validateSources() {
        if (isUniqueRequired()) {
            validateUnique();
        }
        validateNonEmpty();
        if (isSingleSample()) {
            validateSingleSample();
        } else if (isOneFilledSlotRequired()) {
            validateOneFilledSlot();
        }
        validateStates();
    }

    /**
     * Validates the labware for use as active destinations
     */
    public void validateActiveDestinations() {
        if (isUniqueRequired()) {
            validateUnique();
        }
        validateStates();
    }

    /**
     * Checks that all the samples in the labware are in the given bio state.
     * Errors are added where samples are in other bio states.
     * @param bs the required bio state
     */
    public void validateBioState(BioState bs) {
        Set<String> wrongBS = new LinkedHashSet<>();
        Set<Integer> checkedLabwareIds = new HashSet<>();
        labwareLoop:
        for (Labware lw : labware) {
            if (checkedLabwareIds.add(lw.getId())) {
                for (Slot slot : lw.getSlots()) {
                    for (Sample sample : slot.getSamples()) {
                        if (!sample.getBioState().equals(bs)) {
                            wrongBS.add(String.format("(%s, %s)", lw.getBarcode(), sample.getBioState()));
                            continue labwareLoop;
                        }
                    }
                }
            }
        }
        if (!wrongBS.isEmpty()) {
            addError("Labware contains samples not in bio state %s: %s.", bs.getName(), wrongBS);
        }
    }

    /**
     * Checks for repeated labware.
     * If any are found, an error is added.
     */
    public void validateUnique() {
        if (givenBarcodes!=null) {
            validateUniqueBarcodes();
            return;
        }
        if (labware.size() <= 1) {
            return;
        }
        Set<Integer> seenIds = new HashSet<>(labware.size());
        Set<String> dupes = new LinkedHashSet<>();
        for (Labware lw : labware) {
            if (!seenIds.add(lw.getId())) {
                dupes.add(lw.getBarcode());
            }
        }
        if (!dupes.isEmpty()) {
            addError("Labware is repeated: %s.", dupes);
        }
    }

    private void validateUniqueBarcodes() {
        if (givenBarcodes.size() <= 1) {
            return;
        }
        Map<String, Integer> bcCount = new LinkedHashMap<>(labware.size());
        for (Labware lw : labware) {
            if (lw!=null) {
                bcCount.put(lw.getBarcode().toUpperCase(), 0);
            }
        }
        for (String bc : givenBarcodes) {
            String bcu = bc.toUpperCase();
            Integer count = bcCount.get(bcu);
            if (count != null) {
                bcCount.put(bcu, count + 1);
            }
        }
        List<String> dupes = new ArrayList<>();
        for (var entry : bcCount.entrySet()) {
            if (entry.getValue() > 1) {
                dupes.add(entry.getKey());
            }
        }
        if (!dupes.isEmpty()) {
            addError("Labware is repeated: %s.", dupes);
        }
    }

    /**
     * Checks the labware is each nonempty (i.e. contains at least one sample).
     * Adds an error for labware that is empty.
     */
    public void validateNonEmpty() {
        validateState(Labware::isEmpty, "empty");
    }

    /**
     * Checks the labware is not discarded, released, destroyed, or used.
     * Adds an error for each of those cases.
     */
    public void validateStates() {
        if (labware.isEmpty()) {
            return;
        }
        validateState(Labware::isDiscarded, "discarded");
        validateState(Labware::isReleased, "released");
        validateState(Labware::isDestroyed, "destroyed");
        if (!isUsedAllowed()) {
            validateState(Labware::isUsed, "used");
        }
    }

    /**
     * Checks labware is not in a particular state indicated by a predicate.
     * @param predicate the predicate identifying problematic labware
     * @param stateName the name of the problematic state
     */
    public void validateState(Predicate<Labware> predicate, String stateName) {
        if (labware.isEmpty()) {
            return;
        }
        Set<String> bad = labware.stream()
                .filter(predicate)
                .map(Labware::getBarcode)
                .collect(toLinkedHashSet());
        if (!bad.isEmpty()) {
            addError("Labware is %s: %s.", stateName, bad);
        }
    }

    /**
     * Checks for labware containing multiple samples (or one sample in multiple slots).
     * Adds errors for labware in either of those conditions.
     */
    public void validateSingleSample() {
        if (labware.isEmpty()) {
            return;
        }
        Set<String> multiSample = new LinkedHashSet<>();
        Set<String> multiSlot = new LinkedHashSet<>();
        Set<Integer> done = new HashSet<>(labware.size());
        for (Labware lw : labware) {
            if (!done.add(lw.getId())) {
                continue;
            }
            int slotCount = 0;
            int sampleCount = 0;
            int previousSampleId = -1;
            for (Slot slot : lw.getSlots()) {
                if (slot.getSamples().isEmpty()) {
                    continue;
                }
                ++slotCount;
                for (Sample sample : slot.getSamples()) {
                    if (sampleCount==0) {
                        sampleCount = 1;
                        previousSampleId = sample.getId();
                    } else if (sample.getId()!=previousSampleId) {
                        sampleCount = 2;
                    }
                }
            }
            if (sampleCount > 1) {
                multiSample.add(lw.getBarcode());
            } else if (slotCount > 1) {
                multiSlot.add(lw.getBarcode());
            }
        }
        if (!multiSample.isEmpty()) {
            addError("Labware contains multiple samples: %s.", multiSample);
        }
        if (!multiSlot.isEmpty()) {
            addError("Labware contains samples in multiple slots: %s.", multiSlot);
        }
    }

    /**
     * Checks if any labware contains samples in multiple slots.
     * Adds errors for any such labware.
     */
    public void validateOneFilledSlot() {
        if (labware.isEmpty()) {
            return;
        }
        Set<String> multiSlot = new LinkedHashSet<>();
        Set<Integer> done = new HashSet<>(labware.size());

        for (Labware lw : labware) {
            if (!done.add(lw.getId())) {
                continue;
            }
            int slotCount = (int) lw.getSlots().stream().filter(slot -> !slot.getSamples().isEmpty()).count();
            if (slotCount > 1) {
                multiSlot.add(lw.getBarcode());
            }
        }
        if (!multiSlot.isEmpty()) {
            addError("Labware contains samples in multiple slots: %s.", multiSlot);
        }
    }
}
