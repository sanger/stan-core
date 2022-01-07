package uk.ac.sanger.sccp.stan;

import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentMatcher;
import org.mockito.stubbing.Answer;
import uk.ac.sanger.sccp.stan.service.ValidationException;

import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static uk.ac.sanger.sccp.utils.BasicUtils.sameContents;

/**
 * Matchers to use with mockito
 * @author dr6
 */
public class Matchers {

    /**
     * A string matching an argument that is equals to the given string case insensitively
     * @param string the expected string
     */
    public static String eqCi(String string) {
        return argThat(new CaseInsensitiveStringMatcher(string));
    }

    public static String disorderedMatch(String regex, List<String> parts) {
        return argThat(new DisorderedStringMatcher(regex, parts));
    }

    /**
     * A collection matching an argument that has the same content as the given collection (in any order).
     * If the collections are the same size, element counts are ignored
     * (e.g. {@code [3,2,1,2]} will match {@code [1,2,3,1])}).
     * @param collection the collection with the expected elements
     * @param <E> the type of element expected
     * @param <C> the type of collection expected
     */
    public static <E, C extends Collection<E>> C sameElements(C collection) {
        return argThat(new OrderInsensitiveCollectionMatcher<>(collection));
    }

    /**
     * Asserts that an executable throws a validation exception with the given message and problems.
     * @param executable the logic to run that should throw the exception
     * @param exceptionMessage the expected message of the exception
     * @param problems the expected problems listed in the exception
     */
    public static void assertValidationException(final Executable executable, String exceptionMessage,
                                          String... problems) {
        ValidationException ex = assertThrows(ValidationException.class, executable);
        assertThat(ex).hasMessage(exceptionMessage);
        //noinspection unchecked
        assertThat((Collection<Object>) ex.getProblems()).containsExactlyInAnyOrder(problems);
    }

    public static <T> Answer<T> returnArgument() {
        return invocation -> invocation.getArgument(0);
    }

    private static class CaseInsensitiveStringMatcher implements ArgumentMatcher<String> {
        String string;

        public CaseInsensitiveStringMatcher(String string) {
            this.string = string;
        }

        @Override
        public boolean matches(String argument) {
            return (string==null ? argument==null : string.equalsIgnoreCase(argument));
        }
    }

    private static class OrderInsensitiveCollectionMatcher<E, C extends Collection<E>> implements ArgumentMatcher<C> {
        C collection;

        public OrderInsensitiveCollectionMatcher(C collection) {
            this.collection = collection;
        }

        @Override
        public boolean matches(C argument) {
            return sameContents(this.collection, argument);
        }

        @Override
        public String toString() {
            return "in any order "+this.collection;
        }
    }

    public static class DisorderedStringMatcher implements ArgumentMatcher<String> {
        private final Pattern pattern;
        private final List<String> parts;

        public DisorderedStringMatcher(String regex, List<String> parts) {
            this(Pattern.compile(regex), parts);
        }

        public DisorderedStringMatcher(Pattern pattern, List<String> parts) {
            this.pattern = pattern;
            this.parts = parts;
        }

        @Override
        public boolean matches(String string) {
            Matcher m = pattern.matcher(string);
            if (!m.matches()) {
                return false;
            }
            List<String> groups = IntStream.range(1, m.groupCount()+1)
                    .mapToObj(m::group)
                    .collect(toList());
            return sameContents(groups, this.parts);
        }

        @Override
        public String toString() {
            return pattern.pattern()+" with groups "+parts;
        }
    }
}
