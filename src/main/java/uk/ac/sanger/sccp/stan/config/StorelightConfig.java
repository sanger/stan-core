package uk.ac.sanger.sccp.stan.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Config related to connecting to storelight
 * @author dr6
 */
@Configuration
public class StorelightConfig {
    @Value("${storelight.host}")
    String host;
    @Value("${storelight.apikey}")
    String apiKey;

    public String getHost() {
        return this.host;
    }

    public String getApiKey() {
        return this.apiKey;
    }
}
