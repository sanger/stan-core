package uk.ac.sanger.sccp.stan.repo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.sanger.sccp.utils.UCMap;

import javax.persistence.EntityNotFoundException;
import java.util.*;
import java.util.function.Function;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests {@link RepoUtils}
 * @author dr6
 */
public class TestRepoUtils {
    @Test
    public void testGetAllByField() {
        Function<Collection<Integer>, Set<String>> findBy = numbers -> numbers.stream()
                .map(Object::toString)
                .collect(toSet());

        List<String> result = RepoUtils.getAllByField(findBy, List.of(2, 3, 4, 3), Integer::valueOf, "Bad keys: ", null);
        assertThat(result).containsExactly("2", "3", "4", "3");
    }

    @Test
    public void testGetAllByField_none() {
        Function<Collection<Integer>, Set<String>> findBy = mockFunction();
        List<String> result = RepoUtils.getAllByField(findBy, List.of(), Integer::valueOf, "Bad keys: ", null);
        assertThat(result).isEmpty();
        verifyNoInteractions(findBy);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    public void testGetAllByField_missing(boolean anyMissing) {
        Function<Collection<String>, Set<Integer>> findBy = strings -> strings.stream()
                .map(String::trim)
                .filter(string -> !string.isEmpty())
                .map(Integer::valueOf)
                .collect(toSet());
        List<String> values = anyMissing ? List.of("1", "2", "   3 ", "   ", "  2") : List.of("1", "2", "  3 ", "  2");
        if (anyMissing) {
            assertThat(assertThrows(EntityNotFoundException.class, () -> RepoUtils.getAllByField(findBy, values, Object::toString,
                    "Bad key{s}: ", String::trim))).hasMessage("Bad key: [   ]");
            return;
        }
        List<Integer> result = RepoUtils.getAllByField(findBy, values, Object::toString,
                "Bad key{s}: ", String::trim);
        assertThat(result).containsExactly(1,2,3,2);
    }

    @Test
    public void testGetMapByField() {
        Function<Collection<String>, Set<Integer>> findBy = strings -> strings.stream()
                .map(Integer::valueOf)
                .collect(toSet());
        Map<String, Integer> map = RepoUtils.getMapByField(findBy, List.of("1", "2", "3", "2"),
                Object::toString, "Bad key{s}: ");
        assertEquals(Map.of("1", 1, "2", 2, "3", 3), map);
    }

    @Test
    public void testGetMapByField_none() {
        Function<Collection<String>, Set<Integer>> findBy = mockFunction();
        Map<String, Integer> map = RepoUtils.getMapByField(findBy, List.of(),
                Object::toString, "Bad key{s}: ");
        assertThat(map).isEmpty();
        verifyNoInteractions(findBy);
    }

    @Test
    public void testGetMapByField_missing() {
        Function<Collection<String>, Set<Integer>> findBy = strings -> strings.stream()
                .map(String::trim)
                .filter(string -> !string.isEmpty())
                .map(Integer::valueOf)
                .collect(toSet());
        assertThat(assertThrows(EntityNotFoundException.class,
                () -> RepoUtils.getMapByField(findBy, List.of("1", " "), Objects::toString, "Bad key{s}: ")))
                .hasMessage("Bad key: [ ]");
    }

    @Test
    public void testGetUCMapByField() {
        Function<Collection<String>, Set<String>> findBy = strings -> strings.stream()
                .map(s -> "="+s.toUpperCase())
                .collect(toSet());
        UCMap<String> result = RepoUtils.getUCMapByField(findBy, List.of("a", "b", "A", "B", "c", "D"),
                string -> string.substring(1), "Bad key{s}: ");
        assertThat(result).containsExactly(Map.entry("A", "=A"), Map.entry("B", "=B"),
                Map.entry("C", "=C"), Map.entry("D", "=D"));
    }

    @SuppressWarnings("unchecked")
    private static <A, B> Function<A, B> mockFunction() {
        return mock(Function.class);
    }
}
