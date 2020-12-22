package uk.ac.sanger.sccp.utils;

import java.util.*;
import java.util.function.Function;

/**
 * Much copied from the corresponding class in CGAP lims
 * @author dr6
 */
public class BasicUtils {
    private BasicUtils() {}

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
     * Using {@link MessageVar} pluralise a message with a template.
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
     * Do two collections have the same contents (maybe in a different order)?
     * If the collections contain repetitions, this method does <i>not</i> check
     * that they have the same number of repetitions.
     * @param a a collection
     * @param b a collection
     * @return {@code true} if the two collections have the same size and contents
     */
    @SuppressWarnings("SuspiciousMethodCalls")
    public static boolean sameContents(Collection<?> a, Collection<?> b) {
        if (a==b) {
            return true;
        }
        if (a instanceof Set && b instanceof Set) {
            return a.equals(b);
        }
        if (a==null || b==null || a.size()!=b.size()) {
            return false;
        }
        if (a.size() <= 3) {
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
}
