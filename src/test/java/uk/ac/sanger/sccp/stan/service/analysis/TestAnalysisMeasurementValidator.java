package uk.ac.sanger.sccp.stan.service.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.ac.sanger.sccp.stan.request.StringMeasurement;
import uk.ac.sanger.sccp.stan.service.analysis.AnalysisMeasurementValidator.AnalysisType;
import uk.ac.sanger.sccp.stan.service.sanitiser.Sanitiser;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.ac.sanger.sccp.stan.Matchers.genericMock;
import static uk.ac.sanger.sccp.stan.service.analysis.AnalysisMeasurementValidator.RIN_VALUE_NAME;

public class TestAnalysisMeasurementValidator {
    private final Sanitiser<String> simpleSanitiser = (problems, value) -> {
        if (value==null || value.isEmpty()) {
            if (problems!=null) {
                problems.add("No value.");
            }
            return null;
        }
        if (value.indexOf('!') >= 0) {
            if (problems != null){
                problems.add("Bad value: "+value);
            }
            return null;
        }
        if (!value.endsWith("#")) {
            value += "#";
        }
        return value;
    };

    @Test
    public void testMeasurementValidatorFactory() {
        Sanitiser<String> rinSanitiser = genericMock(Sanitiser.class);
        Sanitiser<String> dv200Sanitiser = genericMock(Sanitiser.class);
        AnalysisMeasurementValidatorFactory factory = new AnalysisMeasurementValidatorFactory(rinSanitiser, dv200Sanitiser);

        var val = factory.makeValidator(AnalysisType.RIN);
        assertThat(val).isInstanceOf(AnalysisMeasurementValidator.class);
        AnalysisMeasurementValidator amv = (AnalysisMeasurementValidator) val;
        assertSame(amv.getAnalysisType(), AnalysisType.RIN);
        assertSame(amv.getSanitiser(), rinSanitiser);
        assertThat(amv.getAllowedMeasurementNames()).containsExactlyInAnyOrder(RIN_VALUE_NAME);

        val = factory.makeValidator(AnalysisType.DV200);
        assertThat(val).isInstanceOf(AnalysisMeasurementValidator.class);
        amv = (AnalysisMeasurementValidator) val;
        assertSame(amv.getAnalysisType(), AnalysisType.DV200);
        assertSame(amv.getSanitiser(), dv200Sanitiser);
        assertThat(amv.getAllowedMeasurementNames()).containsExactlyInAnyOrder(AnalysisMeasurementValidator.DV200_VALUE_NAME,
                AnalysisMeasurementValidator.DV200_LOWER_NAME, AnalysisMeasurementValidator.DV200_UPPER_NAME);
    }

    @ParameterizedTest
    @CsvSource(value = {
            ";;",
            "x:20 ; x$:20#;",
            "y!:20 ;; Invalid measurement types given for RIN analysis: [y!]",
            "y:20! ;; Bad value: 20!",
            ":20 ;; Measurement name missing.",
            "x: ;; Measurement value missing.",
            "x:20, y!:20, y!:30, z:50!, a:, b:, :20, :30, c:10 ; x$:20#, c$:10# ; " +
                    "Invalid measurement types given for RIN analysis: [y!]//Bad value: 50!" +
                    "//Measurement name missing.//Measurement value missing."
    }, delimiter = ';')
    public void testValidateMeasurements(String input, String output, String expectedProblemString) {
        List<StringMeasurement> inputSms = parseMeasurements(input);
        List<StringMeasurement> outputSms = parseMeasurements(output);

        AnalysisMeasurementValidator validator = spy(new AnalysisMeasurementValidator(
                AnalysisType.RIN, simpleSanitiser, List.of(RIN_VALUE_NAME)));

        doAnswer(invocation -> {
            String name = invocation.getArgument(0);
            if (name==null || name.isEmpty() || name.indexOf('!') >= 0) {
                return null;
            }
            if (!name.endsWith("$")) {
                name += "$";
            }
            return name;
        }).when(validator).sanitiseName(any());

        doNothing().when(validator).checkCombinations(any());

        assertEquals(outputSms, validator.validateMeasurements(inputSms));
        if (expectedProblemString==null) {
            assertThat(validator.compileProblems()).isEmpty();
        } else {
            assertThat(validator.compileProblems()).containsExactlyInAnyOrder(expectedProblemString.split("//"));
        }
    }

    @Test
    public void testSanitiseName() {
        AnalysisMeasurementValidator validator = spy(new AnalysisMeasurementValidator(
                AnalysisType.RIN, simpleSanitiser, List.of("ALPHA", "BETA")));
        for (String s : new String[] { "ALPHA", "Alpha", "alpha"}) {
            assertEquals("ALPHA", validator.sanitiseName(s));
        }
        for (String s : new String[] { "BETA", "Beta", "beta"}) {
            assertEquals("BETA", validator.sanitiseName(s));
        }
        for (String s : new String[] { null, "", "Bananas" }) {
            assertNull(validator.sanitiseName(s));
        }
    }

    @ParameterizedTest
    @CsvSource({
            "RIN,,",
            "DV200,,",
            "RIN, RIN, ",
            "DV200, DV200, ",
            "DV200, DV200 upper, DV200 upper bound given without lower bound.",
            "DV200, DV200 lower, DV200 lower bound given without upper bound.",
            "DV200, DV200 upper/DV200 lower,",
            "DV200, DV200 upper/DV200 lower/DV200, Bounds and actual value both given for DV200.",
            "RIN, RIN/RIN, Measurement given multiple times for the same labware: [RIN]",
            "RIN, RIN/RIN/RIN, Measurement given multiple times for the same labware: [RIN]",
            "DV200, DV200 upper/DV200 lower/DV200 upper/DV200 lower," +
                    "'Measurements given multiple times for the same labware: [DV200 upper, DV200 lower]'",

            })
    public void checkCombinations(String type, String namesJoined, String expectedProblems) {
        List<StringMeasurement> sms = namesJoined==null ? List.of() :
                Arrays.stream(namesJoined.split("/"))
                        .map(name -> new StringMeasurement(name, "6"))
                        .collect(toList());
        AnalysisType analysisType = AnalysisType.valueOf(type);
        AnalysisMeasurementValidator val = new AnalysisMeasurementValidator(analysisType,
                simpleSanitiser, List.of());
        val.checkCombinations(sms);
        if (expectedProblems==null || expectedProblems.isBlank()) {
            assertThat(val.getProblems()).isEmpty();
        } else {
            assertThat(val.getProblems()).containsExactlyInAnyOrder(expectedProblems.split("/"));
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "false;false;;;",
            "true;false;;;Measurement name missing.",
            "false;true;;;Measurement value missing.",
            "false;false;Apples/Oranges;;Invalid measurement types given for RIN analysis: [Apples, Oranges]",
            "false;false;;Bad values in measurements./Other problems.;Bad values in measurements./Other problems.",
            "true;true;Bananas;Something bad.;Measurement name missing./Measurement value missing./" +
                    "Invalid measurement types given for RIN analysis: [Bananas]/Something bad.",
    }, delimiter=';')
    public void testCompileProblems(boolean anyNameNull, boolean anyValueNull, String invalidNames,
                                    String otherProblems, String expectedOutput) {
        AnalysisMeasurementValidator val = new AnalysisMeasurementValidator(AnalysisType.RIN,
                simpleSanitiser, List.of());
        val.setAnyNameNull(anyNameNull);
        val.setAnyValueNull(anyValueNull);
        if (invalidNames!=null && !invalidNames.isBlank()) {
            Collections.addAll(val.getInvalidMeasurementNames(), invalidNames.split("/"));
        }
        if (otherProblems!=null && !otherProblems.isBlank()) {
            Collections.addAll(val.getProblems(), otherProblems.split("/"));
        }

        if (expectedOutput==null || expectedOutput.isBlank()) {
            assertThat(val.compileProblems()).isEmpty();
        } else {
            assertThat(val.compileProblems()).containsExactlyInAnyOrder(expectedOutput.split("/"));
        }
    }

    private static List<StringMeasurement> parseMeasurements(String string) {
        if (string==null || string.isBlank()) {
            return List.of();
        }
        String[] parts = string.split(",");
        List<StringMeasurement> sms = new ArrayList<>(parts.length);
        for (String part : parts) {
            String[] kv = part.split(":");
            sms.add(new StringMeasurement(kv[0].trim(), kv.length > 1 ? kv[1].trim() : null));
        }
        return sms;
    }
}
