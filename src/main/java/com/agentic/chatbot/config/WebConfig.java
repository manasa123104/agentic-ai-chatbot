package com.agentic.chatbot.config;

import com.agentic.chatbot.service.SiteStyleService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final SiteStyleService styleService;

    public WebConfig(SiteStyleService styleService) {
        this.styleService = styleService;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String loc = styleService.uploadsDir().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(loc);
    }
}
