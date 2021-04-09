package uk.ac.sanger.sccp.stan.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import uk.ac.sanger.sccp.utils.tsv.TsvFileConverter;

import java.util.List;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    // Ensure routing is done by the client
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/{spring:^[a-zA-Z\\d-_]+}")
                .setViewName("forward:/");
        registry.addViewController("/**/{spring:^[a-zA-Z\\d-_]+}")
                .setViewName("forward:/");
    }

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(new TsvFileConverter());
    }
}