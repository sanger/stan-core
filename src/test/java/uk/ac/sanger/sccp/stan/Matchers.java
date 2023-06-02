package uk.ac.sanger.sccp.stan;

import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.Stubber;
import uk.ac.sanger.sccp.stan.service.ValidationException;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
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
     * @param <E> the type of element expected
     * @param <C> the type of collection expected
     * @param collection the collection with the expected elements
     * @param checkSize true if the collections must have the same size
     */
    public static <E, C extends Collection<E>> C sameElements(C collection, boolean checkSize) {
        return argThat(new OrderInsensitiveCollectionMatcher<>(collection, checkSize));
    }

    @SuppressWarnings("unchecked")
    public static <E> ArgumentCaptor<Stream<E>> streamCaptor() {
        return ArgumentCaptor.forClass(Stream.class);
    }

    /**
     * A function mapping the given example input to the given output.
     * @param input example input for the function
     * @param output example output for the function
     * @param <T> the input type of the function
     * @param <R> the output type of the function
     */
    public static <T, R> Function<T, R> functionGiving(T input, R output) {
        return argThat(new FunctionMatcher<>(input, output));
    }

    /**
     * A function that behaves like the example function.
     * The behaviour is tested using the given {@code input}.
     * @param function the example function
     * @param input an example input for the function
     * @param <T> the input type of the function
     * @param <R> the output type of the function
     */
    public static <T, R> Function<T, R> functionLike(Function<T, R> function, T input) {
        return functionGiving(input, function.apply(input));
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

    public static <X> Answer<X> addProblem(final String problem) {
        return addProblem(problem, null);
    }

    public static <X> Answer<X> addProblem(final String problem, X returnValue) {
        return invocation -> {
            Collection<String> problems = invocation.getArgument(0);
            problems.add(problem);
            return returnValue;
        };
    }

    public static <X> Stubber mayAddProblem(final String problem, X returnValue) {
        return (problem == null ? doReturn(returnValue) : doAnswer(addProblem(problem, returnValue)));
    }

    public static Stubber mayAddProblem(final String problem) {
        return (problem==null ? doNothing() : doAnswer(addProblem(problem)));
    }

    public static void assertProblem(Collection<String> problems, String expectedProblem) {
        if (expectedProblem==null) {
            assertThat(problems).isEmpty();
        } else {
            assertThat(problems).containsExactly(expectedProblem);
        }
    }

    public static Transactor mockTransactor(final Transactor mockTransactor) {
        when(mockTransactor.transact(any(), any())).then(invocation -> {
            Supplier<?> sup = invocation.getArgument(1);
            return sup.get();
        });
        return mockTransactor;
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
        boolean checkSize;

        public OrderInsensitiveCollectionMatcher(C collection, boolean checkSize) {
            this.collection = collection;
            this.checkSize = checkSize;
        }

        @Override
        public boolean matches(C argument) {
            return sameContents(this.collection, argument, checkSize);
        }

        @Override
        public String toString() {
            return "in any order "+this.collection;
        }
    }

    private static class FunctionMatcher<T, R> implements ArgumentMatcher<Function<T,R>> {
        T input;
        R output;

        public FunctionMatcher(T input, R output) {
            this.input = input;
            this.output = output;
        }

        @Override
        public boolean matches(Function<T, R> argument) {
            return Objects.equals(argument.apply(this.input), this.output);
        }

        @Override
        public String toString() {
            return "function mapping ["+this.input+"] to ["+this.output+"]";
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
