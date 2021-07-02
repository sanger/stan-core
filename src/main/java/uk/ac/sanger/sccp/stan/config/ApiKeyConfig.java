package uk.ac.sanger.sccp.stan.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Config containing api keys
 * @author dr6
 */
@Configuration
public class ApiKeyConfig {
    @Value("#{${uk.ac.sanger.sccp.stan.apikeys}}")
    Map<String, String> apiKeys;

    public Map<String, String> getApiKeys() {
        return this.apiKeys;
    }

    public String getUsername(String apiKey) {
        return getApiKeys().get(apiKey);
    }
}
