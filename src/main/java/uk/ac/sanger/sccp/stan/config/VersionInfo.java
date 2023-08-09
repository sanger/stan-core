package uk.ac.sanger.sccp.stan.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;

import java.util.Objects;

/**
 * Info about the git commit from build-time
 * @author dr6
 */
@Configuration
@PropertySources(@PropertySource(value="classpath:git.properties", ignoreResourceNotFound=true))
public class VersionInfo {
    @Value("${git.commit.id.describe:}")
    private String describe;
    @Value("${git.commit.id:}")
    private String commit;
    private final String version;

    public VersionInfo(ApplicationContext context) {
        this.version = context.getBeansWithAnnotation(SpringBootApplication.class).values().stream()
                .map(bean -> bean.getClass().getPackage().getImplementationVersion())
                .filter(Objects::nonNull)
                .findAny()
                .orElse(null);
    }

    public String getDescribe() {
        return this.describe;
    }

    public String getCommit() {
        return this.commit;
    }

    public String getVersion() {
        return this.version;
    }
}
