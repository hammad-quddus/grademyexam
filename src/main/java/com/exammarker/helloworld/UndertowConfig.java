package com.exammarker.helloworld;

import io.undertow.UndertowOptions;
import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@Configuration
public class UndertowConfig {

    // 1. Force Undertow's network engine to accept large requests
    @Bean
    public WebServerFactoryCustomizer<UndertowServletWebServerFactory> undertowCustomizer() {
        return factory -> factory.addBuilderCustomizers(builder -> {
            // 150MB in bytes
            builder.setServerOption(UndertowOptions.ENABLE_HTTP2, true);
            builder.setServerOption(UndertowOptions.MAX_ENTITY_SIZE, 157286400L);
        });
    }

    // 2. Force Spring's Servlet Engine to accept large files
    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        
        // Explicitly set single file and total request limits to 150MB
        factory.setMaxFileSize(DataSize.ofMegabytes(150));
        factory.setMaxRequestSize(DataSize.ofMegabytes(150));
        
        return factory.createMultipartConfig();
    }
}