package uk.ac.sanger.sccp.stan.config;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import uk.ac.sanger.sccp.utils.StringTemplate;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author dr6
 */
@Configuration
@PropertySource("classpath:sprint.properties")
public class SprintConfig {
    private static final String TEMPLATE_KEY_START = "#", TEMPLATE_KEY_END = "#";

    @Value("${sprint.host}")
    private String host;
    @Value("${sprint.template_dir}")
    private String templateDir;

    private Map<String, SortedMap<Integer, StringTemplate>> templates;

    public String getHost() {
        return this.host;
    }

    public StringTemplate getTemplate(String templateName, int size) {
        templateName = templateName.toLowerCase();
        Map<Integer, StringTemplate> sizeTemplates = templates.get(templateName);
        if (sizeTemplates==null) {
            throw new IllegalArgumentException("No template listed for "+templateName);
        }
        if (sizeTemplates.size()==1) {
            return sizeTemplates.values().iterator().next();
        }
        StringTemplate template = null;
        for (Map.Entry<Integer, StringTemplate> entry : sizeTemplates.entrySet()) {
            template = entry.getValue();
            if (entry.getKey() >= size) {
                return template;
            }
        }
        return template;
    }

    @Autowired
    public void setTemplateFilenames(@Value("#{${sprint.templates}}") Map<String, String> configMap) throws IOException {
        templates = new HashMap<>(configMap.size());
        final Pattern pattern = Pattern.compile("(\\w+)@(\\d+)");
        for (Map.Entry<String, String> entry : configMap.entrySet()) {
            String key = entry.getKey().toLowerCase();
            Matcher matcher = pattern.matcher(key);
            String labelTypeName;
            int size;
            if (matcher.matches()) {
                labelTypeName = matcher.group(1);
                size = Integer.parseInt(matcher.group(2));
            } else {
                labelTypeName = key;
                size = Integer.MAX_VALUE;
            }
            String filename = entry.getValue();
            if (templateDir!=null) {
                filename = Paths.get(templateDir, filename).toString();
            }
            StringTemplate template = readTemplate(filename);
            templates.computeIfAbsent(labelTypeName, k -> new TreeMap<>()).put(size, template);
        }
    }

    private StringTemplate readTemplate(String filename) throws IOException {
        URL url = Resources.getResource(filename);
        String templateString = Resources.toString(url, Charsets.UTF_8);
        return new StringTemplate(templateString, TEMPLATE_KEY_START, TEMPLATE_KEY_END);
    }
}
