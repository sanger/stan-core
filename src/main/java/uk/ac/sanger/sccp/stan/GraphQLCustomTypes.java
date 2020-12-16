package uk.ac.sanger.sccp.stan;

import graphql.language.StringValue;
import graphql.schema.*;
import uk.ac.sanger.sccp.stan.model.Address;

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
}
