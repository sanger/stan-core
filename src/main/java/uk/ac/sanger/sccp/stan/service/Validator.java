package uk.ac.sanger.sccp.stan.service;

import java.util.function.Consumer;

/**
 * @author dr6
 */
public interface Validator<T> {
    boolean validate(T item, Consumer<String> problemConsumer);
}
