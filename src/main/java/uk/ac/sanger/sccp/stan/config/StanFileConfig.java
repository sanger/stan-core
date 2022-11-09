package uk.ac.sanger.sccp.stan.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Config related to storing files on a network volume
 * @author dr6
 */
@Configuration
public class StanFileConfig {
    @Value("${stan.store.root}")
    String root;
    @Value("${stan.store.directory}")
    String dir;

    public String getRoot() {
        return this.root;
    }

    public String getDir() {
        return this.dir;
    }
}
