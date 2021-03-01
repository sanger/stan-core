package uk.ac.sanger.sccp.stan.service.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * @author dr6
 */
public class StoreException extends RuntimeException {
    public StoreException(String message) {
        super(message);
    }

    public StoreException(ArrayNode errors) {
        this(getErrorsMessage(errors));
    }

    private static String getErrorsMessage(ArrayNode errors) {
        if (errors==null || errors.isEmpty()) {
            return "Operation failed.";
        }
        JsonNode first = errors.get(0);
        if (first != null) {
            JsonNode message = first.get("message");
            if (message != null) {
                return message.asText();
            }
        }
        return errors.toString();
    }
}
