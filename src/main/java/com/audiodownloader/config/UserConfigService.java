package com.audiodownloader.config;

import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

@Service
public class UserConfigService {
    private static final String KEY_API = "audio.downloader.acoust-id-api-key";
    private static final String KEY_MUSIC = "audio.downloader.music-folder";

    private final Path configPath = Path.of(System.getProperty("user.home"), ".audio-downloader", "config.properties");

    public UserConfig load() {
        Properties properties = new Properties();
        try {
            if (Files.exists(configPath)) {
                try (InputStream in = Files.newInputStream(configPath)) {
                    properties.load(in);
                }
            }
        } catch (Exception ignored) {
        }
        return new UserConfig(
                properties.getProperty(KEY_API, ""),
                properties.getProperty(KEY_MUSIC, "")
        );
    }

    public void save(UserConfig config) {
        Properties properties = new Properties();
        properties.setProperty(KEY_API, defaultString(config.acoustIdApiKey()));
        properties.setProperty(KEY_MUSIC, defaultString(config.musicFolder()));
        try {
            Files.createDirectories(configPath.getParent());
            try (OutputStream out = Files.newOutputStream(configPath)) {
                properties.store(out, "Audio Downloader Pro user config");
            }
        } catch (Exception ignored) {
        }
    }

    private String defaultString(String value) {
        return value == null ? "" : value.trim();
    }

    public record UserConfig(String acoustIdApiKey, String musicFolder) {
    }
}
