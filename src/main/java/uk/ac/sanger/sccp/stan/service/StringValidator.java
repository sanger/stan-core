package uk.ac.sanger.sccp.stan.service;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * A tool to perform simple validation on a string
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

    /**
     * Creates a new string validator
     * @param fieldName the name of the field being validated (used in problem messages)
     * @param minLength the minimum length of the field
     * @param maxLength the maximum length of the field
     * @param characterTypes the types of characters allowed in the field
     * @param pattern regular expression pattern to match against string (optional)
     */
    public StringValidator(String fieldName, int minLength, int maxLength, Set<CharacterType> characterTypes,
                           Pattern pattern) {
        this.fieldName = fieldName;
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.characterTypes = characterTypes;
        this.pattern = pattern;
    }

    /**
     * Creates a new string validator
     * @param fieldName the name of the field being validated (used in problem messages)
     * @param minLength the minimum length of the field
     * @param maxLength the maximum length of the field
     * @param characterTypes the types of characters allowed in the field
     */
    public StringValidator(String fieldName, int minLength, int maxLength, Set<CharacterType> characterTypes) {
        this(fieldName, minLength, maxLength, characterTypes, null);
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
    public boolean validate(String string, Consumer<String> problemConsumer) {
        boolean ok = true;
        int sl = string.length();
        if (sl < minLength) {
            problemConsumer.accept(String.format("%s \"%s\" is shorter than the minimum length %s.", fieldName, string, minLength));
            ok = false;
        }
        if (sl > maxLength) {
            problemConsumer.accept(String.format("%s \"%s\" is longer than the maximum length %s.", fieldName, string, maxLength));
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
            problemConsumer.accept(String.format("%s \"%s\" contains invalid characters \"%s\".", fieldName, string, sb));
            ok = false;
        }
        if (ok && pattern!=null) {
            if (!pattern.matcher(string).matches()) {
                problemConsumer.accept(String.format("%s \"%s\" does not match the expected format.", fieldName, string));
                ok = false;
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
