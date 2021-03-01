package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.ac.sanger.sccp.stan.service.StringValidator.CharacterType;

import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link StringValidator}
 * @author dr6
 */
public class StringValidatorTest {

    @ParameterizedTest
    @MethodSource("validatorStrings")
    public void testStringValidator(String string, List<String> expectedProblems) {
        List<String> problems = new ArrayList<>();
        StringValidator validator = new StringValidator("X", 3, 8,
                CharacterType.UPPER, CharacterType.DIGIT);
        final boolean ok = validator.validate(string, problems::add);
        if (expectedProblems.isEmpty()) {
            assertTrue(ok);
            assertEquals(problems, List.of());
        } else {
            assertFalse(ok);
            assertThat(problems).hasSize(expectedProblems.size()).hasSameElementsAs(expectedProblems);
        }
    }

    private static Stream<Arguments> validatorStrings() {
        return Arrays.stream(new String[][]{
                {"A", "X \"A\" is shorter than the minimum length 3."},
                {"AAAAAAAAA", "X \"AAAAAAAAA\" is longer than the maximum length 8."},
                {"abcabc", "X \"abcabc\" contains invalid characters \"abc\"."},
                {"*", "X \"*\" is shorter than the minimum length 3.", "X \"*\" contains invalid characters \"*\"."},
        }).map(arr -> Arguments.of(arr[0], Arrays.asList(arr).subList(1, arr.length)));
    }

    @ParameterizedTest
    @MethodSource("characterTypes")
    public void testCharacterType(String chars, CharacterType ct) {
        for (int i = 0; i < chars.length(); ++i) {
            assertEquals(ct, StringValidator.characterType(chars.charAt(i)));
        }
    }

    private static Stream<Arguments> characterTypes() {
        return Arrays.stream(new Object[][]{
                {"ABZ", CharacterType.UPPER},
                {"abz", CharacterType.LOWER},
                {"019", CharacterType.DIGIT},
                {"-", CharacterType.HYPHEN},
                {"_", CharacterType.UNDERSCORE},
                {" ", CharacterType.SPACE},
                {"@[`{", null},
        }).map(Arguments::of);
    }
}
