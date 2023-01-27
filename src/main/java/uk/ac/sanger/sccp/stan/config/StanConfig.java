package uk.ac.sanger.sccp.stan.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Stan main config
 * @author dr6
 */
@Configuration
public class StanConfig {
    @Value("${stan.root}")
    String root;

    public String getRoot() {
        return this.root;
    }
}
