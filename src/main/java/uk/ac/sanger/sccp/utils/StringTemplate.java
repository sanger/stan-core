package uk.ac.sanger.sccp.utils;

import java.util.*;

import static java.util.Objects.requireNonNull;

/**
 * A templating class.
 * Missing keys are removed from the produced string.
 * @author dr6
 */
public class StringTemplate {
    /**
     * Part of the template: a literal string, and then a key for replacement.
     * Null values are omitted from the final string.
     */
    private static class Part {
        String text;
        String key;
    }

    private final List<Part> parts;
    private final int guessedLength;

    /**
     * Creates a template from the given string using the other strings as start- and end-indicators for keys.
     * @param template the template
     * @param startKey the string that indicates the start of a substitution key
     * @param endKey the string that indicates the end of a substitution key
     * @exception NullPointerException any of the arguments is null
     */
    public StringTemplate(String template, String startKey, String endKey) {
        final int templateLength = template.length();
        final int startKeyLength = startKey.length();
        final int endKeyLength = endKey.length();

        guessedLength = templateLength + 16;

        parts = new ArrayList<>();
        int reached = 0;
        while (true) {
            int i = template.indexOf(startKey, reached);
            if (i < 0) {
                break;
            }
            int j = template.indexOf(endKey, i + startKeyLength);
            if (j < 0) {
                break;
            }
            Part part = new Part();
            if (i > reached) {
                part.text = template.substring(reached, i);
            }
            part.key = template.substring(i+startKeyLength, j);
            parts.add(part);
            reached = j + endKeyLength;
        }
        if (reached < templateLength) {
            Part part = new Part();
            part.text = template.substring(reached);
            parts.add(part);
        }
    }

    /**
     * Performs a substitution using the given keys/values in this template.
     * Any substitution key in the template will be replaced by the associated value in the map.
     * Any substitution key in the template that is not present in the map (or whose value is null)
     * will be replaced by the empty string.
     * @param subs the map of keys to values to substitute into the template
     * @return a templated string.
     * @exception NullPointerException the {@code subs} map is null
     */
    public String substitute(Map<String, String> subs) {
        requireNonNull(subs);
        StringBuilder sb = new StringBuilder(guessedLength);
        for (Part part : parts) {
            if (part.text != null) {
                sb.append(part.text);
            }
            if (part.key != null) {
                String value = subs.get(part.key);
                if (value != null) {
                    sb.append(value);
                }
            }
        }
        return sb.toString();
    }
}
