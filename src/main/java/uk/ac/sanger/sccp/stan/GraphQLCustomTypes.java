package uk.ac.sanger.sccp.stan;

import graphql.language.*;
import graphql.schema.*;
import org.jetbrains.annotations.NotNull;
import uk.ac.sanger.sccp.stan.model.Address;

import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Date;

/**
 * @author dr6
 */
public class GraphQLCustomTypes {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

    public static final GraphQLScalarType ADDRESS = GraphQLScalarType.newScalar()
            .name("Address")
            .description("A 1-indexed row and column, in the form \"B12\" (row 2, column 12) or \"32,15\" (row 32, column 15).")
            .coercing(new Coercing<Address, String>() {
                @Override
                public String serialize(@NotNull Object dataFetcherResult) throws CoercingSerializeException {
                    if (dataFetcherResult instanceof Address) {
                        return dataFetcherResult.toString();
                    }
                    throw new CoercingSerializeException("Unable to serialize "+dataFetcherResult+" as Address.");
                }

                @NotNull
                @Override
                public Address parseValue(@NotNull Object input) throws CoercingParseValueException {
                    if (input instanceof String) {
                        try {
                            return Address.valueOf((String) input);
                        } catch (RuntimeException rte) {
                            throw new CoercingParseValueException("Unable to parse value "+input+" as Address.", rte);
                        }
                    }
                    throw new CoercingParseValueException("Unable to parse value "+input+" as Address.");
                }

                @NotNull
                @Override
                public Address parseLiteral(@NotNull Object input) throws CoercingParseLiteralException {
                    if (input instanceof StringValue) {
                        try {
                            return Address.valueOf(((StringValue) input).getValue());
                        } catch (RuntimeException rte) {
                            throw new CoercingParseValueException("Unable to parse literal "+input+" as Address.", rte);
                        }
                    }
                    throw new CoercingParseValueException("Unable to parse literal "+input+" as Address.");
                }

                @Override
                public @NotNull Value<?> valueToLiteral(@NotNull Object input) {
                    return StringValue.of((String) input);
                }
            })
            .build();

    public static final GraphQLScalarType TIMESTAMP = GraphQLScalarType.newScalar()
            .name("Timestamp")
            .description("A scalar type representing a point in time.")
            .coercing(new Coercing<LocalDateTime, String>() {
                @Override
                public String serialize(@NotNull Object dataFetcherResult) throws CoercingSerializeException {
                    try {
                        LocalDateTime d = toLocalDateTime(dataFetcherResult);
                        if (d != null) {
                            return d.format(DATE_TIME_FORMAT);
                        }
                    } catch (RuntimeException rte) {
                        throw new CoercingSerializeException("Unable to serialize " + dataFetcherResult + " as a timestamp", rte);
                    }
                    throw new CoercingSerializeException("Unable to serialize " + dataFetcherResult + " as a timestamp.");
                }

                @NotNull
                @Override
                public LocalDateTime parseValue(@NotNull Object input) throws CoercingParseValueException {
                    try {
                        LocalDateTime d = toLocalDateTime(input);
                        if (d != null) {
                            return d;
                        }
                    } catch (RuntimeException rte) {
                        throw new CoercingParseValueException("Unable to parse value " + input + " as a timestamp.", rte);
                    }
                    throw new CoercingParseValueException("Unable to parse value " + input + " as a timestamp.");
                }

                @NotNull
                @Override
                public LocalDateTime parseLiteral(@NotNull Object input) throws CoercingParseLiteralException {
                    try {
                        LocalDateTime d = toLocalDateTime(input);
                        if (d != null) {
                            return d;
                        }
                    } catch (RuntimeException rte) {
                        throw new CoercingParseLiteralException("Unable to parse literal " + input + " as a timestamp.", rte);
                    }
                    throw new CoercingParseLiteralException("Unable to parse literal " + input + " as a timestamp.");
                }

                @Override
                public @NotNull Value<?> valueToLiteral(@NotNull Object input) {
                    return StringValue.of(toLocalDateTime(input).format(DATE_TIME_FORMAT));
                }
            })
            .build();

    public static final GraphQLScalarType DATE = GraphQLScalarType.newScalar()
            .name("Date")
            .description("A scalar type representing a date.")
            .coercing(new Coercing<LocalDate, String>() {
                @Override
                public String serialize(@NotNull Object dataFetcherResult) throws CoercingSerializeException {
                    try {
                        LocalDate d = toLocalDate(dataFetcherResult);
                        if (d != null) {
                            return d.toString();
                        }
                    } catch (RuntimeException rte) {
                        throw new CoercingSerializeException("Unable to serialize " + dataFetcherResult + " as a date", rte);
                    }
                    throw new CoercingSerializeException("Unable to serialize " + dataFetcherResult + " as a date.");
                }

                @NotNull
                @Override
                public LocalDate parseValue(@NotNull Object input) throws CoercingParseValueException {
                    try {
                        LocalDate d = toLocalDate(input);
                        if (d != null) {
                            return d;
                        }
                    } catch (RuntimeException rte) {
                        throw new CoercingParseValueException("Unable to parse value " + input + " as a date.", rte);
                    }
                    throw new CoercingParseValueException("Unable to parse value " + input + " as a date.");
                }

                @NotNull
                @Override
                public LocalDate parseLiteral(@NotNull Object input) throws CoercingParseLiteralException {
                    try {
                        LocalDate d = toLocalDate(input);
                        if (d != null) {
                            return d;
                        }
                    } catch (RuntimeException rte) {
                        throw new CoercingParseLiteralException("Unable to parse literal " + input + " as a date.", rte);
                    }
                    throw new CoercingParseLiteralException("Unable to parse literal " + input + " as a date.");
                }

                @Override
                public @NotNull Value<?> valueToLiteral(@NotNull Object input) {
                    return StringValue.of(toLocalDate(input).toString());
                }
            })
            .build();

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof StringValue) {
            return LocalDateTime.parse(((StringValue) value).getValue(), DATE_TIME_FORMAT);
        }
        if (value instanceof IntValue) {
            long epoch = ((IntValue) value).getValue().longValueExact();
            return Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDateTime();
        }
        if (value instanceof String) {
            return LocalDateTime.parse((String) value, DATE_TIME_FORMAT);
        }
        if (value instanceof Long) {
            return Instant.ofEpochMilli((long) value).atZone(ZoneId.systemDefault()).toLocalDateTime();
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime();
        }
        if (value instanceof Date) {
            return ((Date) value).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        }
        return null;
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        if (value instanceof StringValue) {
            return LocalDate.parse(((StringValue) value).getValue());
        }
        if (value instanceof String) {
            return LocalDate.parse((String) value);
        }
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate();
        }
        if (value instanceof Date) {
            return ((Date) value).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        return null;
    }
}
