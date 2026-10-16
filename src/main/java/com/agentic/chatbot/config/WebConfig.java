package com.agentic.chatbot.config;

import com.agentic.chatbot.service.SiteStyleService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final SiteStyleService styleService;

    public WebConfig(SiteStyleService styleService) {
        this.styleService = styleService;
    }

    /**
     * The home chat streams a whole build (minutes with a local model) over one response, so the servlet container's
     * 30 s default for asynchronous responses would cut it off.
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(60L * 60L * 1000L);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String loc = styleService.uploadsDir().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(loc);
    }
}
