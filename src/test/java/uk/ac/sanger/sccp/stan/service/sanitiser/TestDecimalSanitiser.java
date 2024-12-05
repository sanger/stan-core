package uk.ac.sanger.sccp.stan.service.sanitiser;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests {@link DecimalSanitiser}
 */
public class TestDecimalSanitiser {

    @ParameterizedTest
    @CsvSource({
            "200,200.00",
            "-0400.500,-400.50",
            "1,1.00",
            "0000.0000,0.00",
            "8310862419022,8310862419022.00",
    })
    public void testUnboundedSanitiseValid(String input, String expected) {
        Sanitiser<String> san = new DecimalSanitiser("X", 2,null, null);
        assertEquals(expected, san.sanitise(input));
    }

    @ParameterizedTest
    @CsvSource({
            "2,2.00",
            "1,1.00",
            "10,10.00",
            "0000.0000,0.00",
    })
    public void testBoundedSanitiseValid(String input, String expected) {
        Sanitiser<String> san = new DecimalSanitiser("X", 2, BigDecimal.ZERO, BigDecimal.TEN);
        assertEquals(expected, san.sanitise(input));
    }

    @ParameterizedTest
    @CsvSource({
            ",No value given for X",
            "'',No value given for X",
            "-,Invalid value for X: \"-\"",
            "bananas,Invalid value for X: \"bananas\"",
            "83108624190225,Sanitised value too long for X: \"83108624190225.00\"",
    })
    public void testUnboundedSanitiseInvalid(String input, String expectedProblem) {
        List<String> problems = new ArrayList<>(1);
        Sanitiser<String> san = new DecimalSanitiser("X", 2, null, null);
        assertNull(san.sanitise(problems, input));
        assertThat(problems).containsExactly(expectedProblem);
    }

    @ParameterizedTest
    @CsvSource(value={
            "2;bananas;Invalid value for X: \"bananas\"",
            "2;-0.1;Value outside the expected bounds for X: -0.1",
            "2;10.1;Value outside the expected bounds for X: 10.1",
            "2;1.251;Invalid value for X, expected up to 2 decimal places: 1.251",
            "1;1.35;Invalid value for X, expected only 1 decimal place: 1.35",
            "0;5.2;Invalid value for X, expected no decimal places: 5.2",
    }, delimiter=';')
    public void testBoundedSanitiseInvalid(int numDecimalPlaces, String input, String expectedProblem) {
        List<String> problems = new ArrayList<>(1);
        Sanitiser<String> san = new DecimalSanitiser("X", numDecimalPlaces, BigDecimal.ZERO, BigDecimal.TEN);
        assertNull(san.sanitise(problems, input));
        assertThat(problems).containsExactly(expectedProblem);
    }
}