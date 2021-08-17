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

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

import static java.util.Collections.singletonList;

/**
 * The implementations of String utilities used by {@link BasicUtils}.
 * @author dr6
 */
class StringUtils {
    /**
     * Joins a sequence together as a string. This implementation is a little
     * faster than the version without the intermediate {@code String[]} array
     * @param joint String between successive items in the sequence
     * @param seq  items to join
     * @return a string
     */
    static String join(String joint, Collection<?> seq) {
        if (seq.isEmpty()) {
            return "";
        }
        if (seq.size()==1) {
            return String.valueOf(seq.iterator().next());
        }
        int numItems = seq.size();
        String[] items = new String[numItems];
        int len = 0;
        int i = 0;
        for (Object item : seq) {
            String s = String.valueOf(item);
            len += s.length();
            items[i] = s;
            ++i;
        }
        len += joint.length()*(numItems-1);
        StringBuilder sb = new StringBuilder(len);
        sb.append(items[0]);
        for (i = 1; i < numItems; ++i) {
            sb.append(joint);
            sb.append(items[i]);
        }
        return sb.toString();
    }

    /**
     * Produces a string repeating an item multiple times with a given joint in between.
     * @return a string containing repeats of the given string and joint
     */
    static String join_repeat(String item, String joint, int times) {
        if (times <= 0 || joint.length()+item.length()==0) {
            return "";
        }
        if (times==1) {
            return item;
        }
        StringBuilder sb = new StringBuilder(times*(joint.length()+item.length())-joint.length());
        sb.append(item);
        for (int i = 1; i < times; ++i) {
            sb.append(joint);
            sb.append(item);
        }
        return sb.toString();
    }

    /**
     * Insert spaces into a CamelCase string.
     * A space is inserted before an upper case character if it is
     * followed or preceded by a lower case character.
     * Underscores are replaced with spaces.
     * @param text camel case string to insert spaces into
     * @return a string equal to the original string with spaces
     */
    static String spaceCamelCase(String text) {
        return text.replaceAll("_|(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])", " ");
    }

    private static String separateNumber(char delimiter, String number) {
        int startIndex = ((number.charAt(0) == '-') ? 1 : 0);
        if (number.length() > startIndex + 3) {
            StringBuilder sb = new StringBuilder(number);
            for (int i = sb.length()-3; i > startIndex; i -= 3) {
                sb.insert(i, delimiter);
            }
            number = sb.toString();
        }
        return number;
    }

    /**
     * Inserts delimiters ('_') into decimal numbers.
     * Trailing zeroes after a decimal point are stripped.
     * If the number is not an integer then any number to the right of the decimal point
     * will not be delimited.
     * <pre>
     *     e.g. {@code 1000000.000 -> "1_000_000"}
     *          {@code -1000000 -> "-1_000_000"}
     *          {@code 1000.0001 -> "1_000.0001"}
     *          {@code -1000000.1 -> "-1_000_000.1"}
     *          {@code 100 -> "100"}
     * </pre>
     * @param decimal the decimal to separate
     * @return the delimited number string
     */
    static String separateNumber(BigDecimal decimal) {
        if (decimal.compareTo(BigDecimal.ZERO)==0) {
            return "0";
        }
        BigDecimal input = decimal.stripTrailingZeros();
        String inputString = input.toPlainString();
        int decimalPoint = inputString.indexOf('.');
        if (decimalPoint<0) {
            return separateNumber('_', inputString);
        } else {
            return separateNumber('_', inputString.substring(0, decimalPoint)) + inputString.substring(decimalPoint);
        }
    }

    /**
     * Compares strings in such a way that digit sequences of different lengths are sorted according to their
     * numerical values.
     * For instance, {@code "Alpha 9 beta"} will precede {@code "Alpha 10 beta"}
     * It doesn't check for minus signs, because they are
     * just as likely to be hyphens.
     */
    @SuppressWarnings("Duplicates")
    static int compareNumberStrings(String a, String b) {
        int al = a.length();
        int bl = b.length();
        int ml = Math.min(al, bl);
        int i = 0;
        while (i < ml) {
            char ca = a.charAt(i);
            char cb = b.charAt(i);
            if (ca >= '0' && ca <= '9' && cb >= '0' && cb <= '9') {
                int na = 1; // length of number in a
                int za = 0; // number of leading zeroes in a
                if (ca=='0') {
                    za = 1;
                    while (i + na < al && a.charAt(i+na)=='0') {
                        ++za;
                        ++na;
                    }
                }
                while (i + na < al && a.charAt(i+na) >= '0' && a.charAt(i+na) <= '9') {
                    ++na;
                }
                int nb = 1; // length of number in b
                int zb = 0; // number of leading zeroes in b
                if (cb=='0') {
                    zb = 1;
                    while (i + nb < bl && b.charAt(i+nb)=='0') {
                        ++zb;
                        ++nb;
                    }
                }
                while (i + nb < bl && b.charAt(i+nb) >= '0' && b.charAt(i+nb) <= '9') {
                    ++nb;
                }
                // if the number length excluding leading zeroes is different
                if (na-za != nb-zb) {
                    return (na-za < nb-zb ? -1 : 1);
                }
                // Find the first different character in the numbers after any leading zeroes
                for (int j = 0; j < na-za; ++j) {
                    ca = a.charAt(i+za+j);
                    cb = b.charAt(i+zb+j);
                    if (ca!=cb) {
                        return (ca < cb ? -1 : 1);
                    }
                }

                // If the number length is different we need to return nonzero anyway.
                // Make it so that "001" < "01"
                if (na!=nb) {
                    return (nb < na ? -1 : 1);
                }
                i += na;
            } else if (ca!=cb) {
                return (ca < cb ? -1 : 1);
            } else {
                ++i;
            }
        }
        if (al!=bl) {
            return (al < bl ? -1 : 1);
        }
        return 0;
    }

    static String htmlReplacement(char c) {
        switch (c) {
            case '&': return "&amp;";
            case '"': return "&quot;";
            case '<': return "&lt;";
            case '>': return "&gt;";
            default: return null;
        }
    }

    static String htmlEscape(CharSequence input) {
        int i = 0;
        while (i < input.length()) {
            if (htmlReplacement(input.charAt(i))!=null) {
                break;
            }
            ++i;
        }
        if (i >= input.length()) {
            return input.toString();
        }
        StringBuilder sb = new StringBuilder(input);
        while (i < sb.length()) {
            String r = htmlReplacement(sb.charAt(i));
            if (r!=null) {
                sb.replace(i, i+1, r);
                i += r.length();
            } else {
                i += 1;
            }
        }
        return sb.toString();
    }

    static int indexIgnoreCase(String container, String sub, int fromIndex) {
        final int sl = sub.length();
        final int last = container.length()-sl;
        for (int i = Math.max(fromIndex, 0); i <= last; ++i) {
            if (container.regionMatches(true, i, sub, 0, sl)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Does the given string start with the given substring (case insensitive)
     * @param string the container string
     * @param sub the substring
     * @return true if {@code string} starts with {@code sub} ignoring case
     */
    static boolean startsWithIgnoreCase(String string, String sub) {
        return (string!=null && sub!=null && string.length() >= sub.length()
                && string.regionMatches(true, 0, sub, 0, sub.length()));
    }

    static boolean endsWithIgnoreCase(String string, String sub) {
        return (string!=null && sub!=null && string.length() >= sub.length()
                && string.regionMatches(true, string.length()-sub.length(), sub, 0, sub.length()));
    }

    static String lStripZeroes(String value) {
        // short cut
        if (value.length() <= 1 || value.charAt(0)!='0') {
            return value;
        }
        int i = 1;
        int len = value.length() - 1; // don't cut "0" to ""
        while (i < len && value.charAt(i)=='0') {
            i += 1;
        }
        return value.substring(i);
    }

    /**
     * Pluralise a message with {@link MessageVar} and add an unordered list.
     * @param template the {@code MessageVar} template
     * @param items the items
     * @param stringFn an optional function to convert the items to strings
     * @param <E> the type of items being listed
     * @return a string including the message, listing the items
     */
    static <E> String messageAndList(String template, Collection<? extends E> items,
                                            Function<? super E, String> stringFn) {
        StringBuilder sb = new StringBuilder(MessageVar.process(template, items.size()));
        sb.append("<ul>");
        for (E item : items) {
            Object x = (stringFn==null ? item : stringFn.apply(item));
            sb.append("<li>").append(x);
        }
        sb.append("</ul>");
        return sb.toString();
    }

    /**
     * Escape the sql-LIKE symbols in a string
     * (percent, which is any sequence of characters, underscore, which any single character,
     * and backslash, which is the escape character).
     * They are escaped by inserting a backslash before them.
     * @param string the string to escape
     * @return the escaped string
     */
    static String escapeLikeSql(String string) {
        int i = 0;
        int sl = string.length();
        while (i < sl) {
            char ch = string.charAt(i);
            if (ch=='\\' || ch=='%' || ch=='_') {
                break;
            }
            ++i;
        }
        if (i >= sl) {
            return string; // optimum path: no replacements
        }
        StringBuilder sb = new StringBuilder(string);
        sb.insert(i, '\\');
        i += 2;
        while (i < sb.length()) {
            char ch = sb.charAt(i);
            if (ch=='\\' || ch=='%' || ch=='_') {
                sb.insert(i, '\\');
                ++i;
            }
            ++i;
        }
        return sb.toString();
    }

    static StringBuilder replace(StringBuilder sb, String lose, String gain) {
        Objects.requireNonNull(sb);
        Objects.requireNonNull(lose);
        Objects.requireNonNull(gain);
        int i = 0;
        int ll = lose.length();
        int gl = gain.length();
        while (true) {
            i = sb.indexOf(lose, i);
            if (i < 0) {
                break;
            }
            sb.replace(i, i + ll, gain);
            i += gl;
        }
        return sb;
    }

    /**
     * Splits a string to limit the number of characters per chunk.
     * Aims to split on whitespace or on a hyphen.
     * @param string the string to split
     * @param chars the number of chars per chunk
     * @return a list of chunks
     */
    static List<String> splitForWidth(String string, int chars) {
        // NB this will not produce the correct results with a variable-width font
        if (string.length() <= chars) {
            return singletonList(string);
        }
        int start = 0;
        int end = string.length();
        List<String> parts = new ArrayList<>();
        chunkloop:
        while (start + chars < end) {
            int i = start + chars;
            if (Character.isWhitespace(string.charAt(i))) {
                parts.add(string.substring(start, i));
                start = i + 1;
                continue;
            }
            i -= 1;
            while (i > start) {
                char ch = string.charAt(i);
                if (ch == '-') {
                    parts.add(string.substring(start, i + 1));
                    start = i + 1;
                    continue chunkloop;
                }
                if (Character.isWhitespace(ch)) {
                    parts.add(string.substring(start, i));
                    start = i + 1;
                    continue chunkloop;
                }
                --i;
            }
            parts.add(string.substring(start, start + chars));
            start += chars;
        }
        if (start < end) {
            parts.add(string.substring(start));
        }
        return parts;
    }
}
