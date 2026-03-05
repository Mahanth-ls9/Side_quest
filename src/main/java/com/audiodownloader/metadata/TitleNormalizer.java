package com.audiodownloader.metadata;

import org.springframework.stereotype.Component;

@Component
public class TitleNormalizer {
    public String normalize(String rawTitle) {
        if (rawTitle == null) {
            return "";
        }
        String title = rawTitle.trim();
        if (title.isBlank()) {
            return "";
        }

        title = title.replace('_', ' ');
        title = title.replaceAll("(?i)\\b(official\\s+music\\s+video|official\\s+video|official\\s+audio|lyrics\\s+video|lyric\\s+video|visualizer|full\\s+song|audio|video|4k|hd)\\b", "");
        title = title.replaceAll("(?i)[\\(\\[][^\\)\\]]*(official|video|audio|lyrics|lyric|visualizer|4k|hd)[^\\)\\]]*[\\)\\]]", "");

        if (title.contains("|")) {
            title = title.split("\\|", 2)[0];
        }
        if (title.contains(" : ")) {
            title = title.split("\\s:\\s", 2)[0];
        }
        if (title.contains(" - ")) {
            title = title.split("\\s-\\s", 2)[0];
        }

        title = title.replaceAll("\\s{2,}", " ").trim();
        if (title.isBlank()) {
            return rawTitle.trim();
        }
        return title;
    }
}
