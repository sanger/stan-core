package uk.ac.sanger.sccp.utils;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * Tests {@link BasicUtils}
 * @author dr6
 */
public class TestBasicUtils {

    @ParameterizedTest
    @MethodSource("sameContentsArguments")
    public void testSameContents(Collection<?> alpha, Collection<?> beta, boolean expectedresult) {
        assertEquals(expectedresult, sameContents(alpha, beta));
    }

    static Stream<Arguments> sameContentsArguments() {
        Object[] sameContents = {
                List.of(1,2,3,4), List.of(3,4,2,1),
                Set.of(1,2,3), Set.of(3,2,1),
                List.of(1,2,3), Set.of(1,2,3),
                List.of(), Set.of(),
                null, null,
        };
        Object[] diffContents = {
                List.of(1,2,3,4), List.of(1,2,3,5),
                List.of(1,2,3), List.of(1,2,3,5),
                List.of(1,2,3), null,
                List.of(), null
        };
        return Stream.concat(
                IntStream.range(0, sameContents.length/2)
                        .mapToObj(i -> Arguments.of(sameContents[2*i], sameContents[2*i+1], true)),
                IntStream.range(0, diffContents.length/2)
                        .mapToObj(i -> Arguments.of(diffContents[2*i], diffContents[2*i+1], false))
        );
    }

    @ParameterizedTest
    @MethodSource("reprArguments")
    public void testRepr(Object o, String expected) {
        assertEquals(expected, repr(o));
    }

    static Stream<Arguments> reprArguments() {
        Object[] args = {
                null, "null",
                "meringue", "\"meringue\"",
                'x', "'x'",
                17, "17",
                "abc\n \u00D7", "\"abc\\n \\u00D7\"",
                '\0', "'\\u0000'",
                "abc\n\0\tabc\n\0\tabcabc", "\"abc\\n\\u0000\\tabc\\n\\u0000\\tabcabc\"",
                "A\0B\tC\nD\fE\rF\\G\u3333H\"I'", "\"A\\u0000B\\tC\\nD\\fE\\rF\\\\G\\u3333H\\\"I'\"",
        };
        return IntStream.range(0, args.length/2)
                .mapToObj(i -> Arguments.of(args[2*i], args[2*i+1]));
    }

    @Test
    public void testPluralise() {
        String template = "There {is a|are} monkey{s} here and {it|they} want{s|} your banana{s}.";
        assertEquals(pluralise(template, MessageVar.SINGULAR), "There is a monkey here and it wants your banana.");
        assertEquals(pluralise(template, MessageVar.PLURAL), "There are monkeys here and they want your bananas.");

        template = "There {is|are} {a|#} monkey{s}. Hello, {#} monkey{s}.";
        assertEquals(pluralise(template, 1), "There is a monkey. Hello, 1 monkey.");
        assertEquals(pluralise(template, 5), "There are 5 monkeys. Hello, 5 monkeys.");
    }

    @Test
    public void testMessageAndList() {
        String template = "The following {#} banana{s} {was|were} eaten by {a |}monkey{s}:";
        String result = messageAndList(template, Collections.singletonList("Alpha"));
        assertEquals(result, "The following 1 banana was eaten by a monkey:<ul><li>Alpha</ul>");
        result = messageAndList(template, Arrays.asList("Alpha", "Beta"));
        assertEquals(result, "The following 2 bananas were eaten by monkeys:<ul><li>Alpha<li>Beta</ul>");
        result = messageAndList(template, Arrays.asList("Alpha", "Beta"), String::toUpperCase);
        assertEquals(result, "The following 2 bananas were eaten by monkeys:<ul><li>ALPHA<li>BETA</ul>");
    }

    @Test
    public void testReprCollection() {
        assertEquals("[\"Alpha\", \"Beta\\t\"]", reprCollection(List.of("Alpha", "Beta\t")));
    }

    @ParameterizedTest
    @CsvSource(value={
            "Alpha, Alpha",
            "'   Alpha\t', Alpha",
            "A  B  \t C, A B C",
            ",",
            "'',",
            "'   ',",
    })
    public void testTrimAndRequire(String string, String expected) {
        if (expected!=null) {
            assertEquals(trimAndRequire(string, "Bananas"), expected);
        } else {
            String message = "String = bad";
            assertThat(assertThrows(IllegalArgumentException.class, () -> trimAndRequire(string, message)))
                    .hasMessage(message);
        }
    }
}
