package uk.ac.sanger.sccp.stan.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

/**
 * Info about the git commit from build-time
 * @author dr6
 */
@Configuration
@PropertySources(@PropertySource("classpath:git.properties"))
public class GitInfo {
    @Value("${git.commit.id.describe}")
    private String describe;
    @Value("${git.commit.id}")
    private String commit;

    public String getDescribe() {
        return this.describe;
    }

    public String getCommit() {
        return this.commit;
    }
}
