package uk.ac.sanger.sccp.stan.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * @author dr6
 */
@Configuration
@PropertySource("classpath:session.properties")
public class SessionConfig {
    @Value("${maxInactiveMinutes}")
    int maxInactiveMinutes;

    public int getMaxInactiveMinutes() {
        return this.maxInactiveMinutes;
    }
}
