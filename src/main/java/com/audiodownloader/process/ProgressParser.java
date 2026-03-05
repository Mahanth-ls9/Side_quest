package com.audiodownloader.process;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ProgressParser {
    private static final Pattern DOWNLOAD_PATTERN = Pattern.compile(
            "\\[download\\]\\s+([0-9.]+)%.*?at\\s+([^\\s]+).*?ETA\\s+([^\\s]+)"
    );

    public Optional<ProgressUpdate> parse(String line) {
        Matcher matcher = DOWNLOAD_PATTERN.matcher(line);
        if (!matcher.find()) {
            return Optional.empty();
        }
        double progress = Double.parseDouble(matcher.group(1));
        String speed = matcher.group(2);
        String eta = matcher.group(3);
        return Optional.of(new ProgressUpdate(progress, speed, eta, line));
    }
}
