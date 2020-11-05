package uk.ac.sanger.sccp.stan.service;

import java.util.*;
import java.util.function.Consumer;

/**
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

    public StringValidator(String fieldName, int minLength, int maxLength, Set<CharacterType> characterTypes) {
        this.fieldName = fieldName;
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.characterTypes = characterTypes;
    }

    public StringValidator(String fieldName, int minLength, int maxLength, CharacterType... charTypes) {
        this(fieldName, minLength, maxLength, set(charTypes));
    }

    public boolean validate(String string, Consumer<String> problemConsumer) {
        boolean ok = true;
        int sl = string.length();
        if (sl < minLength) {
            problemConsumer.accept(String.format("%s \"%s\" below minimum length %s.", fieldName, string, minLength));
            ok = false;
        }
        if (sl > maxLength) {
            problemConsumer.accept(String.format("%s \"%s\" longer than maximum length %s.", fieldName, string, maxLength));
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
        return ok;
    }

    private static EnumSet<CharacterType> set(CharacterType[] cts) {
        EnumSet<CharacterType> set = EnumSet.noneOf(CharacterType.class);
        if (cts.length > 0) {
            Collections.addAll(set, cts);
        }
        return set;
    }

    private static CharacterType characterType(char ch) {
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
