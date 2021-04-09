package uk.ac.sanger.sccp.stan.service;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static uk.ac.sanger.sccp.utils.BasicUtils.repr;

/**
 * A tool to perform simple validation on a string.
 * @author dr6
 */
public class StringValidator implements Validator<String> {

    public enum CharacterType {
        UPPER, LOWER, DIGIT, HYPHEN, UNDERSCORE, SPACE
    }

    private final String fieldName;
    private final int minLength;
    private final int maxLength;
    private final Set<CharacterType> characterTypes;
    private final Pattern pattern;
    private final boolean badSpaceAllowed;

    /**
     * Creates a new string validator
     * @param fieldName the name of the field being validated (used in problem messages)
     * @param minLength the minimum length of the field
     * @param maxLength the maximum length of the field
     * @param characterTypes the types of characters allowed in the field
     * @param badSpaceAllowed is whitespace permitted at the start or end of the string?
     *                        Is consecutive repeated whitespace permitted?
     * @param pattern regular expression pattern to match against string (optional)
     */
    public StringValidator(String fieldName, int minLength, int maxLength, Set<CharacterType> characterTypes,
                           boolean badSpaceAllowed, Pattern pattern) {
        this.fieldName = fieldName;
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.characterTypes = characterTypes;
        this.pattern = pattern;
        this.badSpaceAllowed = badSpaceAllowed;
    }

    /**
     * Creates a new string validator
     * @param fieldName the name of the field being validated (used in problem messages)
     * @param minLength the minimum length of the field
     * @param maxLength the maximum length of the field
     * @param characterTypes the types of characters allowed in the field
     */
    public StringValidator(String fieldName, int minLength, int maxLength, Set<CharacterType> characterTypes) {
        this(fieldName, minLength, maxLength, characterTypes, false, null);
    }


    /**
     * Creates a new string validator
     * @param fieldName the name of the field being validated (used in problem messages)
     * @param minLength the minimum length of the field
     * @param maxLength the maximum length of the field
     * @param characterTypes the types of characters allowed in the field
     */
    public StringValidator(String fieldName, int minLength, int maxLength, CharacterType... characterTypes) {
        this(fieldName, minLength, maxLength, set(characterTypes));
    }

    /**
     * Checks if a string has suitable length and content.
     * @param string the string to validate
     * @param problemConsumer a function to receive information about the problems
     * @return true if the string is valid; false if problems were found
     */
    @Override
    public boolean validate(String string, Consumer<String> problemConsumer) {
        boolean ok = true;
        int sl = string.length();
        if (sl < minLength) {
            addProblem(problemConsumer, string, "is shorter than the minimum length "+minLength+".");
            ok = false;
        }
        if (sl > maxLength) {
            addProblem(problemConsumer, string, "is longer than the maximum length "+maxLength+".");
            ok = false;
        }
        Set<Character> invalidCharacterSet = null;
        for (int i = 0; i < sl; ++i) {
            char ch = string.charAt(i);
            if (!this.characterTypes.contains(characterType(ch))) {
                if (invalidCharacterSet==null) {
                    invalidCharacterSet = new HashSet<>();
                }
                invalidCharacterSet.add(ch);
            }
        }
        if (invalidCharacterSet!=null && !invalidCharacterSet.isEmpty()) {
            StringBuilder sb = new StringBuilder(invalidCharacterSet.size());
            invalidCharacterSet.stream().sorted().forEach(sb::append);
            addProblem(problemConsumer, string, "contains invalid characters \""+sb+"\".");
            ok = false;
        }
        if (!isBadSpaceAllowed() && characterTypes!=null && characterTypes.contains(CharacterType.SPACE)) {
            ok &= checkWhitespace(string, problemConsumer);
        }
        if (ok && pattern!=null) {
            if (!pattern.matcher(string).matches()) {
                addProblem(problemConsumer, string, "does not match the expected format.");
                ok = false;
            }
        }
        return ok;
    }

    public boolean isBadSpaceAllowed() {
        return this.badSpaceAllowed;
    }

    private String problem(String string, String desc) {
        return fieldName + " " + repr(string) + " " + desc;
    }

    private void addProblem(Consumer<String> problemConsumer, String string, String desc) {
        problemConsumer.accept(problem(string, desc));
    }

    /**
     * Checks for leading, trailing spaces and internal runs of spaces. Literal space character only,
     * since any other whitespace is always disallowed in identifiers.
     * @param string the string to validate
     * @param problemConsumer a function to receive information about the problems
     * @return true if the string is valid; false if problems were found
     */
    private boolean checkWhitespace(String string, Consumer<String> problemConsumer) {
        if (string==null || string.isEmpty()) {
            return true;
        }
        final int len = string.length();
        int leadingSpaces = 0;
        while (leadingSpaces < len && string.charAt(leadingSpaces)==' ') {
            ++leadingSpaces;
        }
        if (leadingSpaces==len) {
            problemConsumer.accept(problem(string, "is all space."));
            return false;
        }
        int trailingSpaces = 0;
        while (trailingSpaces < len && string.charAt(len-trailingSpaces-1)==' ') {
            ++trailingSpaces;
        }
        boolean ok = true;
        if (leadingSpaces > 0 || trailingSpaces > 0) {
            String desc = (leadingSpaces==0) ? "trailing" : (trailingSpaces==0) ? "leading" : "leading and trailing";
            desc += (leadingSpaces + trailingSpaces > 1) ? " spaces." : " space.";
            problemConsumer.accept(problem(string, "has "+desc));
            ok = false;
        }
        int end = len - 1 - trailingSpaces;
        for (int i = leadingSpaces + 2; i < end; ++i) {
            if (string.charAt(i)==' ' && string.charAt(i-1)==' ') {
                problemConsumer.accept(problem(string, "contains consecutive spaces."));
                ok = false;
                break;
            }
        }
        return ok;
    }

    private static EnumSet<CharacterType> set(CharacterType[] cts) {
        EnumSet<CharacterType> set = EnumSet.noneOf(CharacterType.class);
        if (cts.length > 0) {
            Collections.addAll(set, cts);
        }
        return set;
    }

    /**
     * Classifies a character into a {@code CharacterType}.
     * Unclassified characters yield null.
     * @param ch a character
     * @return the character type matched, or null
     */
    public static CharacterType characterType(char ch) {
        if (ch >= 'A' && ch <= 'Z') return CharacterType.UPPER;
        if (ch >= 'a' && ch <= 'z') return CharacterType.LOWER;
        if (ch >= '0' && ch <= '9') return CharacterType.DIGIT;
        switch (ch) {
            case '-': return CharacterType.HYPHEN;
            case '_': return CharacterType.UNDERSCORE;
            case ' ': return CharacterType.SPACE;
        }
        return null;
    }
}
