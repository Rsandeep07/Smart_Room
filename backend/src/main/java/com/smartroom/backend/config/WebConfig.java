package com.smartroom.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroom.backend.security.ApiKeyFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS for the dashboard (build plan Step 6.6) and registration of the ingest key filter. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    private final SmartRoomProperties properties;

    public WebConfig(SmartRoomProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(properties.getDashboard().getAllowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    /**
     * Applies the key check to the ingest endpoints only.
     *
     * <p>A blank {@code smartroom.security.api-key} leaves the filter unregistered and
     * logs a warning. Failing open is the wrong default in general, but silently
     * treating "" as a valid key would be worse: this way the state of ingest
     * authentication is visible in the startup log rather than implied.
     */
    @Bean
    public FilterRegistrationBean<ApiKeyFilter> apiKeyFilter(ObjectMapper objectMapper) {
        FilterRegistrationBean<ApiKeyFilter> registration = new FilterRegistrationBean<>();
        String apiKey = properties.getSecurity().getApiKey();

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("smartroom.security.api-key is not set - the ingest endpoints "
                    + "/api/room/data and /api/detection are UNAUTHENTICATED (Section 20.6)");
            registration.setEnabled(false);
            return registration;
        }

        registration.setFilter(new ApiKeyFilter(
                properties.getSecurity().getApiKeyHeader(), apiKey, objectMapper));
        registration.addUrlPatterns("/api/room/data", "/api/detection");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.setName("apiKeyFilter");
        return registration;
    }
}
