package uk.ac.sanger.sccp.stan.service;

import java.util.Collection;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * A utility to perform sanitisation: that is, to give a sanitised version of a given value.
 * @param <E>
 */
public interface Sanitiser<E> {
    /**
     * Puts a value into the sanitised form.
     * @param value the value to sanitise
     * @return the sanitised value; or null if the given value is invalid
     */
    E sanitise(E value);

    /**
     * Puts the value into the sanitised form.
     * If it fails, adds a problem to the given receptacle.
     * The default problem is not very descriptive.
     * @param problems receptacle for problems (optional)
     * @param value the value to sanitise
     * @return the sanitised value; or null if the given value is invalid
     */
    default E sanitise(Collection<String> problems, E value) {
        E san = this.sanitise(value);
        if (san==null && problems!=null) {
            problems.add("Invalid " + this.fieldName()+": "+repr(value));
        }
        return san;
    }

    /**
     * This string is used in the {@link #sanitise(Collection, E)} method to generate a problem message.
     * By default, this method returns the string {@code "value"}.
     * @return a name for the field being sanitised
     */
    default String fieldName() {
        return "value";
    }

    /**
     * Is the value valid?
     * This method tries to {@link #sanitise} the value to determine if it is valid.
     * @param value the given value
     * @return true if the sanitisation succeeds; false if it fails
     */
    default boolean isValid(E value) {
        return sanitise(value) != null;
    }
}
