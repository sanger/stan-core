package uk.ac.sanger.sccp.stan.service.register.filereader;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * A column used in some sheet of a registration excel file.
 * @author dr6
 */
interface IColumn {
    /**
     * Gets the column matching the given heading, if any.
     */
    static <C extends IColumn> C forHeading(C[] values, String heading) {
        int n = heading.indexOf('(');
        if (n >= 0) {
            heading = heading.substring(0, n);
        }
        final String final_heading = heading.trim();
        return Arrays.stream(values)
                .filter(col -> col.getPattern().matcher(final_heading).matches())
                .findAny()
                .orElse(null);
    }

    /**
     * The data type (String or Integer or LocalDate or Void) expected in the column.
     */
    Class<?> getDataType();

    /**
     * The regex pattern to match against the title of this column.
     */
    Pattern getPattern();

    default boolean isRequired() {
        return getDataType()!=Void.class;
    }
}
