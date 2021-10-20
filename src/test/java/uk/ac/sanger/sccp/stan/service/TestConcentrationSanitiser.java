package uk.ac.sanger.sccp.stan.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link ConcentrationSanitiser}
 * @author dr6
 */
public class TestConcentrationSanitiser {
    private final ConcentrationSanitiser sanitiser = new ConcentrationSanitiser();

    static Stream<Arguments> sanitiserData() {
        return Arrays.stream(new String[][] {
                {null, null},
                {"", null},
                {"  ", null},
                {"-", null},
                {"Hello", null},
                {"-500", "-500.00"},
                {"-1234567890.220000000000", "-1234567890.22"},
                {"+411.5", "411.50"},
                {"11.50E6", "11500000.00"},
                {"24E14", null},
                {"0001234567890123", "1234567890123.00"},
                {"12345678901234", null},
        }).map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("sanitiserData")
    public void testSanitise(String input, String expected) {
        assertEquals(expected, sanitiser.sanitise(input));
    }

    @ParameterizedTest
    @MethodSource("sanitiserData")
    public void testIsValid(String input, String expected) {
        assertEquals(expected!=null, sanitiser.isValid(input));
    }

    @ParameterizedTest
    @MethodSource("sanitiserData")
    public void testSanitiseWithProblems(String input, String expected) {
        List<String> problems = new ArrayList<>(expected==null ? 1 : 0);
        assertEquals(expected, sanitiser.sanitise(problems, input));
        if (expected==null) {
            assertThat(problems).containsExactly(input==null ? "Invalid concentration: null"
                    : "Invalid concentration: \""+input+"\"");
        } else {
            assertThat(problems).isEmpty();
        }
    }
}
