/*
 * Copyright (c) 2015 Genome Research Ltd. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package uk.ac.sanger.sccp.utils;

import java.util.*;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * Class to help generate toString methods.
 * Like lots of other tostring-generators, but supporting {@link BasicUtils#repr repr} for values.
 * Most of this is copied from cgap lims.
 * @author dr6
 */
public class ObjectDescriber {
    private final String prefix;
    private final String suffix;
    private final String separator;
    private final String keyValueSeparator;
    private final List<Object[]> items = new ArrayList<>();

    private boolean omitNullValues = false;
    private boolean reprStringValues = false;

    /**
     * Creates a describer with standard separators, and a prefix including the given name.
     * @param name the name of the entity being described
     */
    public ObjectDescriber(String name) {
        this(name+"{", "}", ", ", "=");
    }

    /**
     * Creates a describer with the given separators.
     * @param prefix the beginning of the description
     * @param suffix the end of the description
     * @param separator the separator between items in the description
     * @param keyValueSeparator the separator between keys and values
     */
    public ObjectDescriber(String prefix, String suffix, String separator, String keyValueSeparator) {
        this.prefix = requireNonNull(prefix, "prefix should not be null");
        this.suffix = requireNonNull(suffix, "suffix should not be null");
        this.separator = requireNonNull(separator, "separator should not be null");
        this.keyValueSeparator = requireNonNull(keyValueSeparator, "keyValueSeparator should not be null");
    }

    /**
     * Add a key/value pair to the description.
     * @param key a string indicating the meaning of the value (must be non-null)
     * @param value the value associated with the given key
     * @return this describer, for chaining
     * @exception NullPointerException if the key is null
     */
    public ObjectDescriber add(String key, Object value) {
        requireNonNull(key, "key should not be null");
        items.add(new Object[] { key, value });
        return this;
    }

    /**
     * Add a key/value pair to the description, if they are not null.
     * If either is null, this method does nothing.
     * @param key a string indicating the meaning of the value
     * @param value the value associated with the given key
     * @return this describer, for chaining
     */
    public ObjectDescriber addIfNotNull(String key, Object value) {
        if (key!=null && value!=null) {
            add(key, value);
        }
        return this;
    }

    /**
     * Sets this describer to omit null values.
     * @return this describer, for chaining
     */
    public ObjectDescriber omitNullValues() {
        this.omitNullValues = true;
        return this;
    }

    /**
     * Sets this describer to use {@link BasicUtils#repr repr} for values that are strings.
     * @return this describer, for chaining
     */
    public ObjectDescriber reprStringValues() {
        this.reprStringValues = true;
        return this;
    }
    
    /**
     * Add a key/value pair to the description, using repr for the value if it is non-null.
     * @param key a string indicating the meaning of the value (must be non-null)
     * @param value the value associated with the given key
     * @return this describer, for chaining
     * @exception NullPointerException if key is null
     */
    public ObjectDescriber addRepr(String key, Object value) {
        if (key!=null && value!=null) {
            value = repr(value.toString());
        }
        return add(key, value);
    }

    /**
     * Adds a key/value pair to the description using repr for the value.
     * If either key or value is null, this method does nothing.
     * @param key a string indicating the meaning of the value
     * @param value the value associated with the given key
     * @return this describer, for chaining
     */
    public ObjectDescriber addReprIfNotNull(String key, Object value) {
        if (value!=null) {
            add(key, repr(value.toString()));
        }
        return this;
    }

    /**
     * Add a key/value pair to the description, where the value is a collection.
     * Ignores the pair if the collection is null or empty.
     * @param key a string indicating the meaning of the value (must be non-null)
     * @param value the value associated with the given key
     * @return this describer, for chaining
     * @exception NullPointerException if the value is non-empty and the key is null
     */
    public ObjectDescriber addIfNotEmpty(String key, Collection<?> value) {
        if (value!=null && !value.isEmpty()) {
            add(key, value);
        }
        return this;
    }

    /**
     * Add a key/value pair to the description, where the value is a map.
     * Ignores the pair if the map is null or empty.
     * @param key a string indicating the meaning of the value (must be non-null)
     * @param value the value associated with the given key
     * @return this describer, for chaining
     * @exception NullPointerException if the value is non-empty and the key is null
     */
    public ObjectDescriber addIfNotEmpty(String key, Map<?,?> value) {
        if (value != null && !value.isEmpty()) {
            add(key, value);
        }
        return this;
    }

    /**
     * Adds a key/value pair to the description. If either is null, nothing happens.
     * Otherwise, the converter is applied to the value before the pair is added to the description.
     * @param key a string indicating the meaning of the value
     * @param value some version of the value associated with the given key
     * @param converter the function to convert the value to the form that should be added
     * @param <V> the type of the value before conversion
     * @return this describer, for chaining
     */
    public <V> ObjectDescriber addIfNotNull(String key, V value, Function<? super V, ?> converter) {
        if (key!=null && value!=null) {
            add(key, converter.apply(value));
        }
        return this;
    }

    @Override
    public String toString() {
        if (items.isEmpty()) {
            return prefix+suffix;
        }
        StringBuilder sb = new StringBuilder(32);
        sb.append(prefix);
        boolean first = true;
        for (Object[] item : items) {
            if (item[1]==null && omitNullValues) {
                continue;
            }
            if (first) {
                first = false;
            } else {
                sb.append(separator);
            }
            sb.append(item[0]).append(keyValueSeparator);
            if (this.reprStringValues && item[1] instanceof String) {
                sb.append(repr(item[1]));
            } else {
                sb.append(item[1]);
            }
        }
        sb.append(suffix);
        return sb.toString();
    }
}
