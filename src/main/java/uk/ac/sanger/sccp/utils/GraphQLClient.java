package uk.ac.sanger.sccp.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Objects;

/**
 * @author dr6
 */
public abstract class GraphQLClient extends BaseHttpClient {
    protected ObjectNode queryObject(String query) {
        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("query", query);
        return objectNode;
    }

    // public to allow for mocking in unit tests
    public GraphQLResponse postQuery(HttpURLConnection connection, String query) throws IOException {
        ObjectNode objectNode = queryObject(query);
        attemptPost(objectNode, connection);
        return toGraphQLResponse(readReturnValue(connection, ObjectNode.class));
    }

    public static GraphQLResponse toGraphQLResponse(ObjectNode object) {
        JsonNode jd = object.get("data");
        ObjectNode data = (jd==null || !jd.isObject()) ? null : (ObjectNode) jd;
        JsonNode je = object.get("errors");
        ArrayNode errors = (je==null || !je.isArray() || je.isEmpty()) ? null : (ArrayNode) je;
        return new GraphQLResponse(data, errors);
    }

    public static class GraphQLResponse {
        private final ObjectNode data;
        private final ArrayNode errors;

        public GraphQLResponse(ObjectNode data, ArrayNode errors) {
            this.data = data;
            this.errors = errors;
        }

        public ObjectNode getData() {
            return this.data;
        }

        public ArrayNode getErrors() {
            return this.errors;
        }

        public boolean hasErrors() {
            return (this.errors!=null && !this.errors.isEmpty());
        }

        @Override
        public String toString() {
            return String.format("(data=%s, errors=%s)", this.data, this.errors);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GraphQLResponse that = (GraphQLResponse) o;
            return (Objects.equals(this.data, that.data)
                    && Objects.equals(this.errors, that.errors));
        }

        @Override
        public int hashCode() {
            return Objects.hash(data, errors);
        }
    }
}
