package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.service.StringValidator.CharacterType;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link StringValidator}
 * @author dr6
 */
public class StringValidatorTest {

    @ParameterizedTest
    @MethodSource("validatorStrings")
    public void testStringValidator(String string, List<String> expectedProblems) {
        StringValidator validator = new StringValidator("X", 3, 8,
                CharacterType.ALPHA, CharacterType.DIGIT);
        testValidate(validator, string, expectedProblems);
    }

    static Stream<Arguments> validatorStrings() {
        return argStream(new String[][]{
                {"A", "X \"A\" is shorter than the minimum length 3."},
                {"AAAAAAAAA", "X \"AAAAAAAAA\" is longer than the maximum length 8."},
                {"%=%=%", "X \"%=%=%\" contains invalid characters \"%=\"."},
                {"*", "X \"*\" is shorter than the minimum length 3.", "X \"*\" contains invalid characters \"*\"."},
        });
    }

    @ParameterizedTest
    @MethodSource("checkWhitespaceArgs")
    public void testCheckWhitespace(String string, List<String> expectedProblems) {
        StringValidator validator = new StringValidator("X", 3, 16,
                CharacterType.ALPHA, CharacterType.SPACE);
        testValidate(validator, string, expectedProblems);
    }

    static Stream<Arguments> checkWhitespaceArgs() {
        return argStream(new String[][] {
                {"", "X \"\" is shorter than the minimum length 3."},
                {"ABCD"},
                {"AB C D"},
                {"     ", "X \"     \" is all space."},
                {" AB C", "X \" AB C\" has leading space."},
                {"AB C  ", "X \"AB C  \" has trailing spaces."},
                {"AB  C", "X \"AB  C\" contains consecutive spaces."},
                {" A  B C  ", "X \" A  B C  \" has leading and trailing spaces.", "X \" A  B C  \" contains consecutive spaces."},
        });
    }

    @ParameterizedTest
    @MethodSource("patternArgs")
    public void testPattern(String string, List<String> expectedProblems) {
        StringValidator validator = new StringValidator("X", 3, 8,
                EnumSet.of(CharacterType.ALPHA, CharacterType.DIGIT, CharacterType.HYPHEN), false,
                Pattern.compile("[A-Z]+-\\d+", Pattern.CASE_INSENSITIVE));
        testValidate(validator, string, expectedProblems);
    }

    static Stream<Arguments> patternArgs() {
        return argStream(new String[][] {
                {"ABC-123"},
                {"abc-123"},
                {"ABC-@123", "X \"ABC-@123\" contains invalid characters \"@\"."},
                {"Bananas", "X \"Bananas\" does not match the expected format."},
                {"ABC-123A", "X \"ABC-123A\" does not match the expected format."},
        });
    }

    private void testValidate(StringValidator validator, String string, List<String> expectedProblems) {
        List<String> problems = new ArrayList<>();
        final boolean ok = validator.validate(string, problems::add);
        assertThat(problems).containsExactlyInAnyOrderElementsOf(expectedProblems);
        assertEquals(expectedProblems.isEmpty(), ok);
    }

    static Stream<Arguments> argStream(String[][] strings) {
        return Arrays.stream(strings)
                .map(arr -> Arguments.of(arr[0], Arrays.asList(arr).subList(1, arr.length)));
    }

    @ParameterizedTest
    @MethodSource("characterTypes")
    public void testCharacterType(String chars, CharacterType characterType) {
        for (int i = 0; i < chars.length(); ++i) {
            assertEquals(characterType, StringValidator.characterType(chars.charAt(i)));
        }
    }

    private static Stream<Arguments> characterTypes() {
        return Arrays.stream(new Object[][]{
                {"ABZabz", CharacterType.ALPHA},
                {"019", CharacterType.DIGIT},
                {"-", CharacterType.HYPHEN},
                {"_", CharacterType.UNDERSCORE},
                {" ", CharacterType.SPACE},
                {"()", CharacterType.PAREN},
                {"/", CharacterType.SLASH},
                {"'", CharacterType.APOSTROPHE},
                {"@[`{", null},
        }).map(Arguments::of);
    }
}
