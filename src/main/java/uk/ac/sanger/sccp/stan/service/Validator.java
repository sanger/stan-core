package uk.ac.sanger.sccp.stan.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A type of object that finds problems.
 * @param <T> the type of object that this validator validates
 * @author dr6
 */
public interface Validator<T> {
    /**
     * Validates an item.
     * @param item the item to be validated
     * @param problemConsumer a consumer to receive messages about problems
     * @return true if the item is found to be valid; false if any problems are found
     */
    boolean validate(T item, Consumer<String> problemConsumer);

    /**
     * Validates the given item. Throws an {@code IllegalArgumentException} if validation fails.
     * @param item the item to be validated
     * @exception IllegalArgumentException if any problems were found
     */
    default void checkArgument(T item) throws IllegalArgumentException {
        List<String> problems = new ArrayList<>();
        validate(item, problems::add);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", problems));
        }
    }
}
