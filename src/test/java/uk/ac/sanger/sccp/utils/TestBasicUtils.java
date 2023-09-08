package uk.ac.sanger.sccp.utils;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import uk.ac.sanger.sccp.stan.service.FindService;

import java.time.DayOfWeek;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static uk.ac.sanger.sccp.utils.BasicUtils.*;

/**
 * Tests {@link BasicUtils}
 * @author dr6
 */
public class TestBasicUtils {

    @ParameterizedTest
    @CsvSource({"alpha,beta,alpha", ",beta,beta", "alpha,,alpha", ",,"})
    public void testCoalesce(Object a, Object b, Object expected) {
        assertEquals(expected, coalesce(a, b));
    }

    @ParameterizedTest
    @MethodSource("sameContentsArguments")
    public void testSameContents(Collection<?> alpha, Collection<?> beta, boolean expectedResult) {
        assertEquals(expectedResult, sameContents(alpha, beta));
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
        assertEquals("null", reprCollection(null));
    }

    @Test
    public void testCommaAndConjunction() {
        assertEquals("", commaAndConjunction(List.of(), "and"));
        assertEquals("Alpha", commaAndConjunction(List.of("Alpha"), "and"));
        assertEquals("Alpha and beta", commaAndConjunction(List.of("Alpha", "beta"), "and"));
        assertEquals("Alpha, beta and gamma", commaAndConjunction(List.of("Alpha", "beta", "gamma"), "and"));
        assertEquals("Alpha, beta, gamma, delta or epsilon",
                commaAndConjunction(List.of("Alpha", "beta", "gamma", "delta", "epsilon"), "or"));
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

    @ParameterizedTest
    @MethodSource("newArrayListArgs")
    public <E> void testNewArrayList(Iterable<E> items) {
        var result = newArrayList(items);
        if (items==null) {
            assertThat(result).isInstanceOf(ArrayList.class).isEmpty();
        } else {
            assertThat(result).isInstanceOf(ArrayList.class).containsExactlyElementsOf(items);
        }
    }

    static Stream<Arguments> newArrayListArgs() {
        //noinspection FunctionalExpressionCanBeFolded
        return Stream.of(
                null, new ArrayList<>(), List.of(), Set.of(),
                new ArrayList<>(List.of("Bananas")), List.of("Bananas"), Set.of("Bananas"),
                List.of("Alpha", "Beta", "Gamma"), (Iterable<String>) (List.of("Delta", "Epsilon", "Zeta")::iterator)
        ).map(Arguments::of);
    }

    @Test
    public void testToLinkedHashSet() {
        LinkedHashSet<Integer> results = Stream.of(1,5,2,4,3,2,4,5,1).collect(toLinkedHashSet());
        assertThat(results).containsExactly(1,5,2,4,3);
    }

    @Test
    public void testInMapWithKeyMapperAndFactory() {
        LinkedHashMap<String, String> map = Stream.of("Alpha", "Beta", "Gamma")
                .collect(inMap(String::toUpperCase, LinkedHashMap::new));
        assertEquals(map, Map.of("ALPHA", "Alpha", "BETA", "Beta", "GAMMA", "Gamma"));

        EnumMap<DayOfWeek, String> dayMap = Stream.of("MONDAY", "TUESDAY")
                .collect(inMap(DayOfWeek::valueOf, () -> new EnumMap<>(DayOfWeek.class)));
        assertThat(dayMap).hasSize(2);
        assertEquals("MONDAY", dayMap.get(DayOfWeek.MONDAY));
        assertEquals("TUESDAY", dayMap.get(DayOfWeek.TUESDAY));

        assertThrows(IllegalStateException.class,
                () -> Stream.of("Alpha", "Beta", "Gamma", "Beta")
                        .collect(inMap(String::toUpperCase, LinkedHashMap::new))
        );
    }

    @Test
    public void testInMapWithKeyMapper() {
        HashMap<String, String> map = Stream.of("Alpha", "Beta", "Gamma")
                .collect(inMap(String::toUpperCase));
        assertEquals(map, Map.of("ALPHA", "Alpha", "BETA", "Beta", "GAMMA", "Gamma"));

        assertThrows(IllegalStateException.class,
                () -> Stream.of("Alpha", "Beta", "Gamma", "Gamma")
                        .collect(inMap(String::toUpperCase))
        );
    }

    @Test
    public void testDescribe() {
        assertEquals("Banana{x=1, y=\"null\"}",
                describe("Banana").add("x", 1).add("y","null").add("z", null)
                        .reprStringValues().omitNullValues().toString());
        assertEquals("Integer{x=1, y=null, z=null}",
                describe(15).add("x", 1).add("y", "null").add("z", "null").toString());
    }

    @Test
    public void testDistinctBySerial() {
        List<Integer> result = Stream.of(1,2,4,5,6,7,3,4,5,6).filter(distinctBySerial(n -> n%3)).collect(Collectors.toList());
        assertEquals(List.of(1,2,6), result);
    }

    @Test
    public void testDistinctUCSerial() {
        List<String> result = Stream.of("Alpha", "Beta", "ALPHA", "alpha", "beta", "Gamma", "GAMMA")
                .filter(distinctUCSerial()).collect(Collectors.toList());
        assertEquals(List.of("Alpha", "Beta", "Gamma"), result);
    }

    @Test
    public void testAsCollection() {
        assertNull(asCollection(null));
        List<Integer> intList = List.of(2,3,5);
        assertSame(intList, asCollection(intList));
        Set<Integer> intSet = Set.of(2,3,5);
        assertSame(intSet, asCollection(intSet));
        //noinspection FunctionalExpressionCanBeFolded
        Iterable<Integer> intIterable = intList::iterator;
        assertEquals(intList, asCollection(intIterable));
    }

    @Test
    public void testAsList() {
        assertNull(asList(null));
        List<Integer> intList = List.of(2,3,5);
        assertSame(intList, asList(intList));
        //noinspection FunctionalExpressionCanBeFolded
        Iterable<Integer> intIterable = intList::iterator;
        assertEquals(intList, asList(intIterable));
    }

    @Test
    public void testStartsWithIgnoreCase() {
        assertTrue(startsWithIgnoreCase("Alpha", "ALP"));
        assertTrue(startsWithIgnoreCase("Alpha", "alp"));
        assertTrue(startsWithIgnoreCase("Alp*a", "ALP*A"));
        assertTrue(startsWithIgnoreCase("Alpha", ""));
        assertTrue(startsWithIgnoreCase("", ""));

        assertFalse(startsWithIgnoreCase("", "a"));
        assertFalse(startsWithIgnoreCase("Alph", "Alpha"));
        assertFalse(startsWithIgnoreCase("Alpha", "Balpha"));
    }

    @Test
    public void testReverseIter() {
        List<Integer> list = List.of(1,2,3,4);
        Iterator<Integer> expected = List.of(4,3,2,1).iterator();
        for (Integer value : reverseIter(list)) {
            assertEquals(value, expected.next());
        }
        assertFalse(expected.hasNext());
    }

    @Test
    public void testContainsDupes() {
        assertTrue(containsDupes(List.of(1,2,3,2)));
        assertFalse(containsDupes(List.of(1,3,5,2)));
    }


    @ParameterizedTest
    @CsvSource({
            "TIS*, \\QTIS\\E.*",
            "*TI%?\\*, .*\\QTI%?\\\\E.*",
    })
    public void testMakeWildcardPattern(String input, String expected) {
        Pattern p = makeWildcardPattern(input);
        assertEquals(expected, p.pattern());
        assertThat(input).matches(p);
        assertThat(p.flags() & Pattern.CASE_INSENSITIVE).isNotZero();
    }
}
