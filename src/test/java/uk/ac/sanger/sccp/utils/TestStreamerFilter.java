package uk.ac.sanger.sccp.utils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test {@link StreamerFilter}
 * @author dr6
 */
public class TestStreamerFilter {

    @ParameterizedTest
    @MethodSource("filterStreamArgs")
    public void testFilterStream(Collection<String> initialSource,
                                 Collection<Character> chars, Collection<Integer> lengths,
                                 Collection<String> defaultSource, Collection<String> expected) {
        StreamerFilter<String> sf = new StreamerFilter<>();
        if (initialSource!=null) {
            sf.setSource(() -> initialSource);
        }
        sf.addFilter(chars, s -> s.charAt(0), (cs) -> cs.stream().map(c -> c+"x").collect(toList()));
        sf.addFilter(lengths, String::length, (ns) -> ns.stream().map("a"::repeat).collect(toList()));
        if (!sf.hasSource()) {
            sf.setSource(() -> defaultSource);
        }
        assertThat(sf.filterStream()).containsExactlyElementsOf(expected);
    }

    static Stream<Arguments> filterStreamArgs() {
        List<String> initialSource = List.of("xyz", "xy", "xyzz", "yzz");
        List<String> defaultSource = List.of("Alpha", "Beta");

        return Arrays.stream(new Object[][] {
                {initialSource, null, null, null, initialSource},
                {initialSource, null, null, defaultSource, initialSource},
                {null, null, null, defaultSource, defaultSource},
                {initialSource, Set.of('a','b','x'), null, defaultSource, initialSource.subList(0,3)},
                {initialSource, null, Set.of(2,3), defaultSource, List.of("xyz", "xy", "yzz")},
                {initialSource, null, Set.of(3,11), defaultSource, List.of("xyz", "yzz")},
                {null, List.of('a','b','c'), null, null, List.of("ax", "bx", "cx")},
                {null, List.of('a','b','c'), Set.of(2,3,4), null, List.of("ax", "bx", "cx")},
                {null, List.of('a','b','c'), Set.of(3,4), null, List.of()},
                {null, null, List.of(3,4), null, List.of("aaa", "aaaa")},
                {initialSource, Set.of('x','j'), Set.of(3), defaultSource, List.of("xyz")},
        }).map(Arguments::of);
    }
}
