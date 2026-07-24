package com.nylg.zziiaicodemother.config;

import com.nylg.zziiaicodemother.manager.AvatarFileManager;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private AvatarFileManager avatarFileManager;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = avatarFileManager.resolveAvatarRoot().toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/static/avatar/**")
                .addResourceLocations(location);
    }
}
