package com.audiodownloader.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class AppConfigInitializer {
    private final AppProperties appProperties;
    private final UserConfigService userConfigService;

    public AppConfigInitializer(AppProperties appProperties, UserConfigService userConfigService) {
        this.appProperties = appProperties;
        this.userConfigService = userConfigService;
    }

    @PostConstruct
    public void init() {
        UserConfigService.UserConfig config = userConfigService.load();
        if (config.acoustIdApiKey() != null && !config.acoustIdApiKey().isBlank()) {
            appProperties.setAcoustIdApiKey(config.acoustIdApiKey());
        }
        if (config.musicFolder() != null && !config.musicFolder().isBlank()) {
            appProperties.setMusicFolder(config.musicFolder());
        }
    }
}
