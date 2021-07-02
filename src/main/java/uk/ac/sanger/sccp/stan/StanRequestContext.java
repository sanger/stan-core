package uk.ac.sanger.sccp.stan;

/**
 * Context holding an api key and associated username for a request.
 * Both fields may be null, indicating that the user did not use an api key.
 * @author dr6
 */
public class StanRequestContext {
    public static final String API_KEY_HEADER = "STAN-APIKEY";

    private final String apiKey, username;

    public StanRequestContext(String apiKey, String username) {
        this.apiKey = apiKey;
        this.username = username;
    }

    public String getApiKey() {
        return this.apiKey;
    }

    public String getUsername() {
        return this.username;
    }

    @Override
    public String toString() {
        return String.format("StanRequestContext{apiKey=%s, username=%s}",
                getApiKey()==null? "null": "********",
                username==null? "null": ('"'+username+'"'));
    }
}
