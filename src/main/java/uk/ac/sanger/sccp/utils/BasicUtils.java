package uk.ac.sanger.sccp.utils;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.*;
import java.util.function.*;
import java.util.regex.Pattern;
import java.util.stream.*;

import static java.util.stream.Collectors.joining;

/**
 * Much copied from the corresponding class in CGAP lims
 * @author dr6
 */
public class BasicUtils {
    /**
     * The pattern used in {@link #trimAndRequire} to identify runs of whitespace
     */
    private static final Pattern RUN_OF_WHITESPACE = Pattern.compile("\\s+");

    private BasicUtils() {}

    /**
     * Returns the first (if any) non-null value.
     * If {@code a} is non-null, returns {@code a}; otherwise returns {@code b}
     * @param a first value
     * @param b second value
     * @param <T> type of value
     * @return {@code a} if it is non-null, otherwise {@code b}
     */
    public static <T> T coalesce(T a, T b) {
        return (a==null ? b : a);
    }

    /**
     * Returns a string representation of the given object.
     * If it is a string it will be in quote marks and unprintable
     * characters will be shown as unicode insertions.
     * @param o object to represent
     * @return a string
     */
    public static String repr(Object o) {
        if (o==null) {
            return "null";
        }
        if (o instanceof CharSequence) {
            return StringRepr.repr((CharSequence) o);
        }
        if (o instanceof Character) {
            return StringRepr.repr((char) o);
        }
        return o.toString();
    }

    /**
     * Reprs each item in a stream and returns a joined string.
     * @param stream a stream of strings
     * @return a comma-space-separated string in square brackets.
     */
    public static String reprStream(Stream<String> stream) {
        return stream.map(BasicUtils::repr).collect(Collectors.joining(", ", "[", "]"));
    }

    /**
     * Reprs each item in a collection and returns a joined string.
     * If the collection is null, returns {@code "null"}
     * @param items a collection of strings
     * @return a comma-space-separated string in square brackets.
     */
    public static String reprCollection(Collection<String> items) {
        if (items==null) {
            return "null";
        }
        return reprStream(items.stream());
    }

    /**
     * Combines the given strings with commas and a conjunction at the end.
     * E.g. {@code "or"} or {@code "and"}.
     * Does not add an Oxford comma.
     * @param items the strings to combine
     * @param conjunction the word before the last item
     * @return a string combining the given strings with commas and the given conjunction.
     */
    public static String commaAndConjunction(Collection<String> items, String conjunction) {
        int n = items.size();
        if (n==0) {
            return "";
        }
        if (n==1) {
            return items.iterator().next();
        }
        if (n==2) {
            var iter = items.iterator();
            String first = iter.next();
            String second = iter.next();
            return first + " " + conjunction + " " + second;
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (String item : items) {
            sb.append(item);
            ++i;
            if (i==n-1) {
                sb.append(' ').append(conjunction).append(' ');
            } else if (i < n) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    /**
     * Pluralise a message with {@link MessageVar} and add an unordered list.
     * @param template the {@code MessageVar} template
     * @param items the items
     * @param stringFn an optional function to convert the items to strings
     * @param <E> the type of items being listed
     * @return a string including the message, listing the items
     */
    public static <E> String messageAndList(String template, Collection<? extends E> items,
                                            Function<? super E, String> stringFn) {
        return StringUtils.messageAndList(template, items, stringFn);
    }

    /**
     * Using {@link MessageVar} pluralise a message with a template.<br/>
     * E.g. <code>pluralise("There {is|are} {#} light{s}.", numLights)</code>
     * @param template a template with substitutions baked in
     * @param number the number indicating whether the message should be pluralised or singularised
     * @return the processed string
     */
    public static String pluralise(String template, int number) {
        return MessageVar.process(template, number);
    }

    /**
     * Pluralise a message with {@link MessageVar} and add an unordered list.
     * @param template the {@code MessageVar} template
     * @param items the items
     * @return a string including the message, listing the items
     */
    public static String messageAndList(String template, Collection<?> items) {
        return StringUtils.messageAndList(template, items, null);
    }


    /**
     * Do two collections have the same size and contents (maybe in a different order)?
     * If the collections contain repetitions, this method does <i>not</i> check
     * that they have the same number of repetitions.
     * @param a a collection
     * @param b a collection
     * @return {@code true} if the two collections have the same contents and size
     */
    public static boolean sameContents(Collection<?> a, Collection<?> b) {
        return sameContents(a, b, true);
    }

    /**
     * Do two collections have the same contents (maybe in a different order)?
     * If the collections contain repetitions, this method does <i>not</i> check
     * that they have the same number of repetitions.
     * @param a a collection
     * @param b a collection
     * @param checkSize whether to check the two collections are the same size
     * @return {@code true} if the two collections have the same contents
     */
    @SuppressWarnings("SuspiciousMethodCalls")
    public static boolean sameContents(Collection<?> a, Collection<?> b, boolean checkSize) {
        if (a==b) {
            return true;
        }
        if (a instanceof Set && b instanceof Set) {
            return a.equals(b);
        }
        if (a==null || b==null) {
            return false;
        }
        int len = a.size();
        if (checkSize && len != b.size()) {
            return false;
        }
        if (len <= 3 && (checkSize || b.size() <= 3)) {
            return (a.containsAll(b) && b.containsAll(a));
        }
        if (!(a instanceof Set)) {
            a = new HashSet<>(a);
        }
        if (!(b instanceof Set)) {
            b = new HashSet<>(b);
        }
        return a.equals(b);
    }

    /**
     * Creates a new arraylist using the argument as its contents.
     * If <tt>items</tt> is null, the new list will be empty.
     * If <tt>items</tt> contains objects, the new list will contain those objects.
     * @param items the contents for the new list
     * @param <E> the content type of the list
     * @return a new arraylist
     */
    public static <E> ArrayList<E> newArrayList(Iterable<? extends E> items) {
        if (items==null) {
            return new ArrayList<>();
        }
        if (items instanceof Collection) {
            return new ArrayList<>((Collection<? extends E>) items);
        }
        ArrayList<E> list = new ArrayList<>();
        items.forEach(list::add);
        return list;
    }

    /**
     * Collector that produces a {@code LinkedHashSet} (an insertion-ordered set).
     * @param <T> the type of elements
     * @return a collector
     */
    public static <T> Collector<T, ?, LinkedHashSet<T>> toLinkedHashSet() {
        return Collectors.toCollection(LinkedHashSet::new);
    }

    /**
     * Collector to a map where the values are the input objects
     * @param keyMapper a mapping function to produce keys
     * @param mapFactory a supplier providing a new empty {@code Map}
     *                   into which the results will be inserted
     * @param <T> the type of the input elements
     * @param <K> the output type of the key mapping function
     * @param <M> the type of the resulting {@code Map}
     * @return a {@code Collector} which collects elements into a {@code Map}
     *             whose keys are the result of applying a key mapping function to the input
     *             elements, and whose values are input elements
     */
    public static <T, K, M extends Map<K, T>> Collector<T, ?, M> inMap(Function<? super T, ? extends K> keyMapper,
                                                                       Supplier<M> mapFactory) {
        return Collectors.toMap(keyMapper, Function.identity(), illegalStateMerge(), mapFactory);
    }

    /**
     * Collector to a hashmap where the values are the input objects
     * @param keyMapper a mapping function to produce keys
     * @param <T> the type of the input elements
     * @param <K> the output type of the key mapping function
     * @return a {@code Collector} which collects elements into a {@code Map}
     *             whose keys are the result of applying a key mapping function to the input
     *             elements, and whose values are input elements
     */
    public static <T, K> Collector<T, ?, HashMap<K,T>> inMap(Function<? super T, ? extends K> keyMapper) {
        return Collectors.toMap(keyMapper, Function.identity(), illegalStateMerge(), HashMap::new);
    }

    /**
     * A binary operator that throws an illegal state exception. Used as the merge function for collecting
     * to a map whose incoming keys are expected to be unique.
     * @param <U> the type of value
     * @return a binary operator that throws an {@link IllegalStateException}
     */
    public static <U> BinaryOperator<U> illegalStateMerge() {
        return (a, b) -> {throw new IllegalStateException("Duplicate keys found in map.");};
    }

    /**
     * Gets a describer to help generate the toString description for an object.
     * @param name the name of the object (e.g. its type)
     * @return a describer
     */
    public static ObjectDescriber describe(String name) {
        return new ObjectDescriber(name);
    }

    /**
     * Gets a describer to help generate the toString description for an object.
     * @param object the object being described
     * @return a describer
     */
    public static ObjectDescriber describe(Object object) {
        return describe(object.getClass().getSimpleName());
    }

    /**
     * Trims a string, replaces runs of whitespace with a space, and checks that it is non-null and nonempty.
     * @param text the string
     * @return the adjusted string
     * @exception IllegalArgumentException if the string is null or empty (after trimming)
     */
    public static String trimAndRequire(String text, String message) throws IllegalArgumentException {
        if (text==null) {
            throw new IllegalArgumentException(message);
        }
        text = RUN_OF_WHITESPACE.matcher(text.trim()).replaceAll(" ");
        if (text.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }

    /**
     * Predicate for filtering out duplicates from a stream.
     * This uses a simple hashset to track what was seen: it is not guaranteed
     * to work for a parallel stream.
     * The keys (the results of the supplied function) must be hashable.
     * @param function The function to extract the keys from the objects in the stream.
     * @param <T> The stream type.
     * @param <V> The type of key used to distinguish objects in the stream.
     * @return a predicate for filtering out duplicates.
     */
    public static <T, V> Predicate<T> distinctBySerial(Function<? super T, V> function) {
        final Set<V> seen = new HashSet<>();
        return x -> seen.add(function.apply(x));
    }

    /**
     * Predicate for filtering out duplicates in a string case insensitively.
     * This uses a simple hashset to track what was seen: it is not guaranteed
     * to work for a parallel stream.
     * Case insensitivity for the purposes of this method means that the strings are equal
     * when converted to upper case.
     * @return a predicate for filtering out duplicate strings, case insensitively.
     */
    public static Predicate<String> distinctUCSerial() {
        final Set<String> seen = new HashSet<>();
        return x -> seen.add(x==null ? null : x.toUpperCase());
    }

    /**
     * Gets the given iterable as a collection. If it already is a collection (or is null),
     * it is cast and returned.
     * If it is not a collection, its contents are added to a new collection, which is returned.
     * @param iterable an iterable
     * @param <E> the type of contents in the iterable
     * @return the iterable cast as a collection, or a new collection with the iterable's contents
     */
    public static <E> Collection<E> asCollection(Iterable<E> iterable) {
        if (iterable==null || iterable instanceof Collection) {
            return (Collection<E>) iterable;
        }
        List<E> list = new ArrayList<>();
        for (E item : iterable) {
            list.add(item);
        }
        return list;
    }

    /**
     * Gets the given iterable as a list. If it is already a list (or is null), it is cast and returned.
     * If it is not a list, its contents are added to a new list, which is returned.
     * @param iterable an iterable
     * @param <E> the type of contents in the iterable
     * @return the iterable cast as a list, or a new list with the iterable's contents
     */
    public static <E> List<E> asList(Iterable<E> iterable) {
        if (iterable==null || iterable instanceof List) {
            return (List<E>) iterable;
        }
        return newArrayList(iterable);
    }

    /**
     * Returns a list containing the concatenated contents of two given lists.
     * If one argument is null, the other is returned.
     * Otherwise, if either list is empty, the other is returned. Otherwise, the lists are
     * concatenated into a new list, which is returned.
     * @param a a list or null
     * @param b a list or null
     * @return a list containing the elements of the two lists, or null if both are null
     * @param <E> the type of element in the lists
     */
    public static <E> List<E> concat(List<E> a, List<E> b) {
        if (a==null) return b;
        if (b==null) return a;
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        List<E> combined = new ArrayList<>(a.size() + b.size());
        combined.addAll(a);
        combined.addAll(b);
        return combined;
    }

    /**
     * Makes an {@link Iterable} to iterate through a list from end to start.
     * @param list the list to reverse-iterate
     * @return an {@code Iterable<T>}
     */
    public static <T> Iterable<T> reverseIter(List<T> list) {
        return () -> new ReverseIterator<>(list.listIterator(list.size()));
    }


    /**
     * Does the given string start with the given substring, ignoring its case?
     * @param string the containing string
     * @param sub the substring
     * @return true if {@code string} starts with {@code sub}, ignoring case; false otherwise
     */
    public static boolean startsWithIgnoreCase(String string, String sub) {
        return StringUtils.startsWithIgnoreCase(string, sub);
    }

    /**
     * Does the given string end with the given substring, ignoring its case?
     * @param string the containing string
     * @param sub the substring
     * @return true if {@code string} ends with {@code sub}, ignoring case; false otherwise
     */
    public static boolean endsWithIgnoreCase(String string, String sub) {
        return StringUtils.endsWithIgnoreCase(string, sub);
    }

    /**
     * Escape the sql-LIKE symbols in a string
     * (percent, which is any sequence of characters, underscore, which is any single character,
     * and backslash, which is the escape character).
     * They are escaped by inserting a backslash before them.
     * @param string the string to escape
     * @return the escaped string
     */
    public static String escapeLikeSql(String string) {
        return StringUtils.escapeLikeSql(string);
    }

    /**
     * Convert a string with <tt>*</tt> as a wildcard to a string with <tt>%</tt> suitable to be
     * used in an SQL LIKE comparison.
     * @param string the string to convert
     * @return the converted string
     */
    public static String wildcardToLikeSql(String string) {
        return escapeLikeSql(string).replaceAll("\\*+", "%");
    }

    /**
     * Makes a case-insensitive regular expression pattern to match the given string reading <tt>*</tt> as a wildcard.
     * @param wildcardString a string containing <tt>*</tt> as wildcards
     * @return the regular expression pattern object
     */
    public static Pattern makeWildcardPattern(String wildcardString) {
        String[] parts = wildcardString.split("\\*+", -1);
        String regex = Arrays.stream(parts)
                .map(part -> part.isEmpty() ? part : Pattern.quote(part))
                .collect(joining(".*"));
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    /**
     * Makes a case-insensitive regular expression pattern to match any of the given strings,
     * reading <tt>*</tt> as a wildcard.
     * @param wildcardStrings a collection of strings that may contain <tt>*</tt>  as a wildcard.
     * @return the regular expression pattern object
     */
    public static Pattern makeWildcardPattern(Collection<String> wildcardStrings) {
        if (wildcardStrings.size()==1) {
            return makeWildcardPattern(wildcardStrings.iterator().next());
        }
        List<String> options = new ArrayList<>();
        for (String string : wildcardStrings) {
            String option;
            if (string.indexOf('*') < 0) {
                option = Pattern.quote(string);
            } else {
                option = Arrays.stream(string.split("\\*+", -1))
                        .map(part -> part.isEmpty() ? part : Pattern.quote(part))
                        .collect(joining(".*"));
            }
            options.add(option);
        }
        String regex = StringUtils.join("|", options);
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    /**
     * Is the given string null or empty?
     */
    public static boolean nullOrEmpty(String string) {
        return (string==null || string.isEmpty());
    }

    /** Is the given collection null or empty? */
    public static boolean nullOrEmpty(Collection<?> c) {
        return (c==null || c.isEmpty());
    }

    /** Is the given map null or empty? **/
    public static boolean nullOrEmpty(Map<?,?> m) {
        return (m==null || m.isEmpty());
    }

    /**
     * If the string is empty, return null. Otherwise, return the string.
     * @param string the string that may be empty
     * @return the nonempty string, or null
     */
    public static String emptyToNull(String string) {
        return (string==null || string.isEmpty() ? null : string);
    }

    /**
     * If the given list is non-null, it is returned. Otherwise, returns the immutable empty list.
     * @param list list or null
     * @return a non-null list
     * @param <E> the type of element in the list
     */
    @NonNull public static <E> List<E> nullToEmpty(@Nullable List<E> list) {
        return (list==null ? List.of() : list);
    }

    /**
     * If the given map is non-null, it is returned. Otherwise, returns the immutable empty map.
     * @param map map or null
     * @return a non-null map
     * @param <K> map's key-type
     * @param <V> map's value-type
     */
    @NonNull public static <K,V> Map<K,V> nullToEmpty(@Nullable Map<K,V> map) {
        return (map==null ? Map.of() : map);
    }

    public static <K,V> LinkedHashMap<K,V> orderedMap(K key1, V value1, K key2, V value2) {
        LinkedHashMap<K,V> map = new LinkedHashMap<>(2);
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }

    /**
     * Makes a map entry of the given key and value, each of which may be null.
     */
    public static <K,V> Map.Entry<K,V> simpleEntry(K key, V value) {
        return new AbstractMap.SimpleImmutableEntry<>(key, value);
    }

    /**
     * Returns a stream of the given iterable
     * @param iterable an iterable
     * @return a stream
     * @param <E> the type of objects being iterated
     */
    public static <E> Stream<E> stream(Iterable<E> iterable) {
        if (iterable instanceof Collection) {
            return ((Collection<E>) iterable).stream();
        }
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    /**
     * Does the given iterable contain any duplicates?
     * Duplication is checked using a hashset.
     * @param items iterable items
     * @return true if any dupe is found; otherwise false
     * @param <E> the type of object iterated
     */
    public static <E> boolean containsDupes(Iterable<E> items) {
        final Set<E> seen = new HashSet<>();
        for (E item : items) {
            if (!seen.add(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Streams pairs of items from the given list.
     * Each combination of items will be emitted once, via the given mapper function,
     * with the order of the items used as arguments consistent with the order they
     * occur in the list
     * @param items the items to stream
     * @param mapper mapper to combine the pairs of items into one combined object
     * @return a stream of pairs of items
     * @param <E> the type of item in the list
     */
    public static <E, P> Stream<P> streamPairs(final List<? extends E> items, final BiFunction<E,E,P> mapper) {
        final int n = items.size();
        return IntStream.range(0, n-1).boxed()
                .flatMap(i -> IntStream.range(i+1, n)
                        .mapToObj(j -> mapper.apply(items.get(i), items.get(j))));
    }
}
