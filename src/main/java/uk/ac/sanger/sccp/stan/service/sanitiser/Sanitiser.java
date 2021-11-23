package uk.ac.sanger.sccp.stan.service.sanitiser;

import java.util.Collection;

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
    default E sanitise(E value) {
        return sanitise(null, value);
    }

    /**
     * Puts the value into the sanitised form.
     * If it fails, adds a problem to the given receptacle.
     * The default problem is not very descriptive.
     * @param problems receptacle for problems (optional)
     * @param value the value to sanitise
     * @return the sanitised value; or null if the given value is invalid
     */
    E sanitise(Collection<String> problems, E value);

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
