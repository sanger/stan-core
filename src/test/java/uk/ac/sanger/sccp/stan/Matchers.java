package uk.ac.sanger.sccp.stan;

import org.mockito.ArgumentMatcher;

import static org.mockito.ArgumentMatchers.argThat;

/**
 * @author dr6
 */
public class Matchers {
    public static ArgumentMatcher<String> equalsIgnoreCase(String string) {
        return new CaseInsensitiveStringMatcher(string);
    }

    public static String eqCi(String string) {
        return argThat(equalsIgnoreCase(string));
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
}
