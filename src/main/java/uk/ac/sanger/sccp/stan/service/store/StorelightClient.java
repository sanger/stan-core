package uk.ac.sanger.sccp.stan.service.store;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.ac.sanger.sccp.stan.config.StorelightConfig;
import uk.ac.sanger.sccp.utils.GraphQLClient;

import java.io.IOException;
import java.net.*;

/**
 * Client for talking to storelight
 * @author dr6
 */
@Component
public class StorelightClient extends GraphQLClient {
    private final StorelightConfig storelightConfig;

    @Autowired
    public StorelightClient(StorelightConfig storelightConfig) {
        this.storelightConfig = storelightConfig;
        setTimeout(10_000); // in case requests might take a while
    }

    protected URL getURL() throws MalformedURLException {
        return new URL(storelightConfig.getHost());
    }

    public GraphQLResponse postQuery(String query, String user) throws IOException {
        HttpURLConnection connection = openConnection(getURL());
        try {
            setHeaders(connection, user);
            return postQuery(connection, query);
        } finally {
            connection.disconnect();
        }
    }

    protected void setHeaders(HttpURLConnection connection, String user) {
        setUsualHeaders(connection);
        connection.setRequestProperty("STORELIGHT-APIKEY", storelightConfig.getApiKey());
        if (user!=null && !user.isEmpty()) {
            connection.setRequestProperty("STORELIGHT-USER", user);
        }
    }
}
