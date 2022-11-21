package uk.ac.sanger.sccp.stan.repo;

import uk.ac.sanger.sccp.utils.BasicUtils;
import uk.ac.sanger.sccp.utils.UCMap;

import javax.persistence.EntityNotFoundException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

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
        if (values.isEmpty()) {
            return List.of();
        }
        Iterable<E> found = findBy.apply(values);
        final Function<? super V, ? extends V> canon = (canonicalise==null ? Function.identity() : canonicalise);
        Map<V, E> map = BasicUtils.stream(found) .collect(BasicUtils.toMap(getField.andThen(canon)));
        LinkedHashSet<V> missing = new LinkedHashSet<>(values.size() - map.size());
        List<E> items = new ArrayList<>(values.size());
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
     * @param canonicalise (optional) function to canonicalise the values
     *        (e.g. put strings in upper case for deduplication)
     * @param mapSupplier optional supplier to get a new empty map to put items into
     * @param emptyMapSupplier optional supplier to get an empty map to return empty
     * @return a map of the found items from their values
     * @exception EntityNotFoundException any specified items cannot be found
      */
    public static <V,E,C extends Collection<V>, M extends Map<V,E>> M getMapByField(
            Function<C, ? extends Iterable<E>> findBy, C values, Function<E, V> getField,
            String errorText, Function<? super V, ? extends V> canonicalise,
            Supplier<M> mapSupplier, Supplier<M> emptyMapSupplier) throws EntityNotFoundException {
        if (values.isEmpty()) {
            if (emptyMapSupplier!=null) {
                return emptyMapSupplier.get();
            }
            return mapSupplier.get();
        }
        Iterable<E> found = findBy.apply(values);
        final Function<? super V, ? extends V> canon = (canonicalise==null ? Function.identity() : canonicalise);
        M map = BasicUtils.stream(found).collect(BasicUtils.toMap(getField.andThen(canon), mapSupplier));
        LinkedHashSet<V> missing = values.stream()
                .filter(value -> map.get(value)==null)
                .collect(BasicUtils.toLinkedHashSet());
        if (!missing.isEmpty()) {
            throw new EntityNotFoundException(BasicUtils.pluralise(errorText, missing.size()) + missing);
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
        return getMapByField(findBy, values, getField, errorText, null, HashMap::new, Map::of);
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
        return getMapByField(findBy, values, getField, errorText, null, UCMap::new, null);
    }
}
