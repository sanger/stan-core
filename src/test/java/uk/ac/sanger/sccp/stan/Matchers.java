package uk.ac.sanger.sccp.stan;

import org.mockito.ArgumentMatcher;
import org.mockito.stubbing.Answer;

import java.util.Collection;

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

    public static <T> Answer<T> returnArgument(int index) {
        return invocation -> invocation.getArgument(index);
    }

    public static <T> Answer<T> returnArgument() {
        return returnArgument(0);
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
}
