package uk.ac.sanger.sccp.stan.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
}