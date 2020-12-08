package uk.ac.sanger.sccp.stan;

import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.schema.*;
import uk.ac.sanger.sccp.stan.model.Address;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * @author dr6
 */
public class GraphQLCustomTypes {
    public static final GraphQLScalarType ADDRESS = GraphQLScalarType.newScalar()
            .name("Address")
            .description("A 1-indexed row and column, in the form \"B12\" (row 2, column 12) or \"32,15\" (row 32, column 15).")
            .coercing(new Coercing<Address, String>() {
                @Override
                public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
                    if (dataFetcherResult instanceof Address) {
                        return dataFetcherResult.toString();
                    }
                    throw new CoercingSerializeException("Unable to serialize "+dataFetcherResult+" as Address.");
                }

                @Override
                public Address parseValue(Object input) throws CoercingParseValueException {
                    if (input instanceof String) {
                        try {
                            return Address.valueOf((String) input);
                        } catch (RuntimeException rte) {
                            throw new CoercingParseValueException("Unable to parse value "+input+" as Address.", rte);
                        }
                    }
                    throw new CoercingParseValueException("Unable to parse value "+input+" as Address.");
                }

                @Override
                public Address parseLiteral(Object input) throws CoercingParseLiteralException {
                    if (input instanceof StringValue) {
                        try {
                            return Address.valueOf(((StringValue) input).getValue());
                        } catch (RuntimeException rte) {
                            throw new CoercingParseValueException("Unable to parse literal "+input+" as Address.", rte);
                        }
                    }
                    throw new CoercingParseValueException("Unable to parse literal "+input+" as Address.");
                }
            })
            .build();

    public static final GraphQLScalarType TIMESTAMP = GraphQLScalarType.newScalar()
            .name("Timestamp")
            .description("A scalar type representing a point in time.")
            .coercing(new Coercing<Timestamp, String>() {
                @Override
                public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
                    try {
                        Timestamp ts = toTimestamp(dataFetcherResult);
                        if (ts != null) {
                            return ts.toString();
                        }
                    } catch (RuntimeException rte) {
                        throw new CoercingSerializeException("Unable to serialize " + dataFetcherResult + " as a timestamp", rte);
                    }
                    throw new CoercingSerializeException("Unable to serialize " + dataFetcherResult + " as a timestamp.");
                }

                @Override
                public Timestamp parseValue(Object input) throws CoercingParseValueException {
                    try {
                        Timestamp ts = toTimestamp(input);
                        if (ts != null) {
                            return ts;
                        }
                    } catch (RuntimeException rte) {
                        throw new CoercingParseValueException("Unable to parse value " + input + " as a timestamp.", rte);
                    }
                    throw new CoercingParseValueException("Unable to parse value " + input + " as a timestamp.");
                }

                @Override
                public Timestamp parseLiteral(Object input) throws CoercingParseLiteralException {
                    try {
                        Timestamp ts = toTimestamp(input);
                        if (ts != null) {
                            return ts;
                        }
                    } catch (RuntimeException rte) {
                        throw new CoercingParseLiteralException("Unable to parse literal " + input + " as a timestamp.", rte);
                    }
                    throw new CoercingParseLiteralException("Unable to parse literal " + input + " as a timestamp.");
                }
            })
            .build();

    private static Timestamp toTimestamp(Object value) {
        if (value instanceof Timestamp) {
            return (Timestamp) value;
        }
        if (value instanceof StringValue) {
            return Timestamp.valueOf(((StringValue) value).getValue());
        }
        if (value instanceof IntValue) {
            return new Timestamp(((IntValue) value).getValue().longValueExact());
        }
        if (value instanceof String) {
            return Timestamp.valueOf((String) value);
        }
        if (value instanceof Long) {
            return new Timestamp((Long) value);
        }
        if (value instanceof Date) {
            return new Timestamp(((Date) value).getTime());
        }
        if (value instanceof LocalDateTime) {
            return Timestamp.valueOf((LocalDateTime) value);
        }
        return null;
    }
}
