package com.baklibra26.common.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties({CorsProperties.class})
public class WebConfiguration {

    @Bean
    public WebMvcConfigurer corsConfigurer(CorsProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins(properties.allowedOrigins().toArray(String[]::new))
                        .allowedMethods(properties.allowedMethods().toArray(String[]::new))
                        .allowedHeaders(properties.allowedHeaders().toArray(String[]::new))
                        .maxAge(properties.maxAge().toSeconds());
            }
        };
    }

}
