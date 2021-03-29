package uk.ac.sanger.sccp.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collector;

/**
 * A map with upper case strings for keys.
 * Keys are converted to upper case when they are put into the map.
 * Keys are converted to upper case when they are being looked up in the map.
 * @author dr6
 */
public class UCMap<V> implements Map<String, V> {
    private final Map<String, V> inner;

    public UCMap(int initialCapacity) {
        inner = new HashMap<>(initialCapacity);
    }

    public UCMap() {
        inner = new HashMap<>();
    }

    public UCMap(Map<String, V> contents) {
        this(contents.size());
        this.putAll(contents);
    }

    @Override
    public int size() {
        return inner.size();
    }

    @Override
    public boolean isEmpty() {
        return inner.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return inner.containsKey(upcase(key));
    }

    @Override
    public boolean containsValue(Object value) {
        return inner.containsValue(value);
    }

    @Override
    public V get(Object key) {
        return inner.get(upcase(key));
    }

    @Nullable
    @Override
    public V put(String key, V value) {
        return inner.put(upcase(key), value);
    }

    @Override
    public V remove(Object key) {
        return inner.remove(upcase(key));
    }

    @Override
    public void putAll(@NotNull Map<? extends String, ? extends V> m) {
        for (var entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        inner.clear();
    }

    @NotNull
    @Override
    public Set<String> keySet() {
        return inner.keySet(); // NB may allow insertion of non-canonical keys
    }

    @NotNull
    @Override
    public Collection<V> values() {
        return inner.values();
    }

    @NotNull
    @Override
    public Set<Entry<String, V>> entrySet() {
        return inner.entrySet();
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof Map && this.inner.equals(obj));
    }

    @Override
    public int hashCode() {
        return inner.hashCode();
    }

    private static <K> K upcase(K key) {
        if (key instanceof String) {
            //noinspection unchecked
            key = (K) ((String) key).toUpperCase();
        }
        return key;
    }

    @Override
    public String toString() {
        return "UCMap("+inner+")";
    }

    /**
     * Collector to collect to a {@code UCMap} from strings to the input objects.
     * @param keyMapper mapper from input objects to strings
     * @param <T> the type of input object
     * @return a collector to collect to a {@code UCMap}
     */
    public static <T> Collector<T, ?, UCMap<T>> toUCMap(Function<? super T, String> keyMapper) {
        return BasicUtils.toMap(keyMapper, UCMap::new);
    }

    public static <T> UCMap<T> from(Function<T, String> keyMapper, T value) {
        UCMap<T> map = new UCMap<>(1);
        map.put(keyMapper.apply(value), value);
        return map;
    }

    @SafeVarargs
    public static <T> UCMap<T> from(Function<T, String> keyMapper, T... values) {
        UCMap<T> map = new UCMap<>(values.length);
        for (T value : values) {
            map.put(keyMapper.apply(value), value);
        }
        return map;
    }

    public static <T> UCMap<T> from(Collection<? extends T> items, Function<T, String> keyMapper) {
        UCMap<T> map = new UCMap<>(items.size());
        for (T item : items) {
            map.put(keyMapper.apply(item), item);
        }
        return map;
    }
}
