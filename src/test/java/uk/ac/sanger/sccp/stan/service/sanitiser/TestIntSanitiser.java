package uk.ac.sanger.sccp.stan.service.sanitiser;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests {@link IntSanitiser}
 */
public class TestIntSanitiser {

    @ParameterizedTest
    @CsvSource({
            "200,200",
            "-0400,-400",
            "1,1",
            "0000,0",
    })
    public void testUnboundedSanitiseValid(String input, String expected) {
        Sanitiser<String> san = new IntSanitiser("X", null, null);
        assertEquals(expected, san.sanitise(input));
    }

    @ParameterizedTest
    @CsvSource({
            "20,20",
            "1,1",
            "100,100",
            "0000,0",
    })
    public void testBoundedSanitiseValid(String input, String expected) {
        Sanitiser<String> san = new IntSanitiser("X", 0, 100);
        assertEquals(expected, san.sanitise(input));
    }

    @ParameterizedTest
    @CsvSource({
            ",No value given for X",
            "'',No value given for X",
            "-,Invalid value for X: \"-\"",
            "bananas,Invalid value for X: \"bananas\"",
            "999999999999,Invalid value for X: \"999999999999\"",
    })
    public void testUnboundedSanitiseInvalid(String input, String expectedProblem) {
        List<String> problems = new ArrayList<>(1);
        Sanitiser<String> san = new IntSanitiser("X", null, null);
        assertNull(san.sanitise(problems, input));
        assertThat(problems).containsExactly(expectedProblem);
    }

    @ParameterizedTest
    @CsvSource({
            "-,Invalid value for X: \"-\"",
            "bananas,Invalid value for X: \"bananas\"",
            "999999999999,Invalid value for X: \"999999999999\"",
            "-1,Value outside the expected bounds for X: -1",
            "101,Value outside the expected bounds for X: 101",
    })
    public void testBoundedSanitiseInvalid(String input, String expectedProblem) {
        List<String> problems = new ArrayList<>(1);
        Sanitiser<String> san = new IntSanitiser("X", 0, 100);
        assertNull(san.sanitise(problems, input));
        assertThat(problems).containsExactly(expectedProblem);
    }
}