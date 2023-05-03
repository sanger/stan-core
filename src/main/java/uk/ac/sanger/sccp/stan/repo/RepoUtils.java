package uk.ac.sanger.sccp.stan.repo;

import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import javax.persistence.EntityNotFoundException;
import java.util.*;
import java.util.function.*;

import static uk.ac.sanger.sccp.utils.BasicUtils.coalesce;

/**
 * Utils for repos
 * @author dr6
 */
public class RepoUtils {
    /**
     * Gets the indicated items from the given repo function, in the order matching the values.
     * Throws an exception if any items cannot be found.
     * @param <V> the type of values being given to look up the entities
     * @param <E> the type of entity being looked up
     * @param <C> the type of collection of values given
     * @param findBy function to find entities matching the given values
     * @param values the values to use to look up the entities
     * @param getField function to get a value from an entity
     * @param errorText the text of the error, which will be followed by the list of missing values
     * @param canonicalise (optional) function to canonicalise the values
     *        (e.g. put strings in upper case for deduplication)
     * @return list of matching items
     * @exception EntityNotFoundException any specified items cannot be found
     */
    public static <V, E, C extends Collection<V>> List<E> getAllByField(Function<C, ? extends Iterable<E>> findBy, C values,
                                                                        Function<E, V> getField, String errorText,
                                                                        Function<? super V, ? extends V> canonicalise)
            throws EntityNotFoundException {
        return getCollectionByField(findBy, values, getField, errorText, canonicalise, ArrayList::new, List::of);
    }

    /**
     * Gets the indicated items from the given repo function, in a set.
     * Throws an exception if any items cannot be found.
     * @param <V> the type of values being given to look up the entities
     * @param <E> the type of entity being looked up
     * @param <C> the type of collection of values given
     * @param findBy function to find entities matching the given values
     * @param values the values to use to look up the entities
     * @param getField function to get a value from an entity
     * @param errorText the text of the error, which will be followed by the list of missing values
     * @param canonicalise (optional) function to canonicalise the values
     *        (e.g. put strings in upper case for deduplication)
     * @return set of matching items
     * @exception EntityNotFoundException any specified items cannot be found
     */
    public static <V, E, C extends Collection<V>> Set<E> getSetByField(Function<C, ? extends Iterable<E>> findBy,
                                                                       C values, Function<E, V> getField,
                                                                       String errorText,
                                                                       Function<? super V, ? extends V> canonicalise)
            throws EntityNotFoundException {
        return getCollectionByField(findBy, values, getField, errorText, canonicalise, HashSet::new, Set::of);
    }

    /**
     * Gets the indicated items from the given repo function, in a collection.
     * Throws an exception if any items cannot be found.
     * @param <V> the type of values being given to look up the entities
     * @param <E> the type of entity being looked up
     * @param <C> the type of collection of values given
     * @param <R> the type of collection the entities will be returned in
     * @param findBy function to find entities matching the given values
     * @param values the values to use to look up the entities
     * @param getField function to get a value from an entity
     * @param errorText the text of the error, which will be followed by the list of missing values
     * @param canonicalise (optional) function to canonicalise the values
     *        (e.g. put strings in upper case for deduplication)
     * @param clSupplier supplier for a collection, given a suggested capacity
     * @param emptyClSupplier supplier for an empty collection (optional)
     * @return collection of matching items
     * @exception EntityNotFoundException any specified items cannot be found
     */
    public static <V, E, C extends Collection<V>, R extends Collection<E>> R getCollectionByField(Function<C, ? extends Iterable<E>> findBy,
                                                                       C values, Function<E, V> getField,
                                                                       String errorText,
                                                                       Function<? super V, ? extends V> canonicalise,
                                                                       IntFunction<? extends R> clSupplier,
                                                                       Supplier<? extends R> emptyClSupplier)
            throws EntityNotFoundException {
        if (values.isEmpty()) {
            return (emptyClSupplier!=null ? emptyClSupplier.get() : clSupplier.apply(0));
        }
        Iterable<E> found = findBy.apply(values);
        final Function<? super V, ? extends V> canon = coalesce(canonicalise, Function.identity());
        Map<V, E> map = BasicUtils.stream(found).collect(BasicUtils.inMap(getField.andThen(canon)));
        LinkedHashSet<V> missing = new LinkedHashSet<>(values.size() - map.size());
        R items = clSupplier.apply(values.size());
        for (V value : values) {
            E item = map.get(canon.apply(value));
            if (item == null) {
                missing.add(value);
            } else {
                items.add(item);
            }
        }
        if (!missing.isEmpty()) {
            throw new EntityNotFoundException(BasicUtils.pluralise(errorText, missing.size()) + missing);
        }
        return items;
    }

    /**
     * Gets the indicated items from the given repo function, in a map from the values.
     * Throws an exception if any items cannot be found.
     * @param <V> the type of values being given to look up the entities
     * @param <E> the type of entity being looked up
     * @param <C> the type of collection of values given
     * @param <M> the type of map returned
     * @param findBy function to find entities matching the given values
     * @param values the values to use to look up the entities
     * @param getField function to get a value from an entity
     * @param errorText the text of the error, which will be followed by the list of missing values
     * @param mapSupplier optional supplier to get a new empty map to put items into
     * @param emptyMapSupplier optional supplier to get an empty map to return empty
     * @return a map of the found items from their values
     * @exception EntityNotFoundException any specified items cannot be found
      */
    public static <V,E,C extends Collection<V>, M extends Map<V,E>> M getMapByField(
            Function<C, ? extends Iterable<E>> findBy, C values, Function<E, V> getField, String errorText,
            Supplier<M> mapSupplier, Supplier<M> emptyMapSupplier) throws EntityNotFoundException {
        if (values.isEmpty()) {
            return coalesce(emptyMapSupplier, mapSupplier).get();
        }

        M map = BasicUtils.stream(findBy.apply(values)).collect(BasicUtils.inMap(getField, mapSupplier));

        if (map.size() < values.size()) {
            LinkedHashSet<V> missing = values.stream()
                    .filter(value -> map.get(value) == null)
                    .collect(BasicUtils.toLinkedHashSet());
            if (!missing.isEmpty()) {
                throw new EntityNotFoundException(BasicUtils.pluralise(errorText, missing.size()) + missing);
            }
        }
        return map;
    }

    /**
     * Gets the indicated items from the given repo function, in a map from the values.
     * Throws an exception if any items cannot be found.
     * @param <V> the type of values being given to look up the entities
     * @param <E> the type of entity being looked up
     * @param <C> the type of collection of values given
     * @param findBy function to find entities matching the given values
     * @param values the values to use to look up the entities
     * @param getField function to get a value from an entity
     * @param errorText the text of the error, which will be followed by the list of missing values
     * @return a map of the found items from their values
     * @exception EntityNotFoundException any specified items cannot be found
     */
    public static <V,E,C extends Collection<V>> Map<V,E> getMapByField(
            Function<C, ? extends Iterable<E>> findBy, C values, Function<E, V> getField,
            String errorText) throws EntityNotFoundException {
        return getMapByField(findBy, values, getField, errorText, HashMap::new, Map::of);
    }

    /**
     * Gets the indicates items from the given repo function, in a UCMap from the values.
     * Throws an exception if any items cannot be found.
     * @param <E> the type of entity to look up
     * @param <C> the type of collection given containing the values
     * @param findBy function to find entities matching the given values
     * @param values the values to use to look up the entities
     * @param getField function to get a value from an entity
     * @param errorText the text of the error, which will be followed by the list of missing values
     * @return a UCMap of the found items from their values
     * @exception EntityNotFoundException any specified items cannot be found
     */
    public static <E,C extends Collection<String>> UCMap<E> getUCMapByField(
            Function<C, ? extends Iterable<E>> findBy, C values, Function<E, String> getField,
            String errorText) throws EntityNotFoundException {
        return getMapByField(findBy, values, getField, errorText, UCMap::new, null);
    }
}
