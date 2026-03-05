package com.audiodownloader.metadata;

import org.springframework.stereotype.Component;

@Component
public class TitleNormalizer {
    public String normalize(String rawTitle) {
        return normalize(rawTitle, "");
    }

    public String normalize(String rawTitle, String channel) {
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
        title = title.replaceAll("(?i)\\b(prod\\.?|produced\\s+by)\\b.*$", "");
        title = title.replaceAll("@\\w+", "");
        title = title.replaceAll("^\"|\"$", "");

        if (title.contains("|")) {
            title = title.split("\\|", 2)[0];
        }

        if (title.matches("(?i)^.+\\s-\\s*title\\s*track\\b.*$")) {
            title = title.replaceAll("(?i)^(.+?)\\s-\\s*title\\s*track\\b.*$", "$1");
        }

        if (title.contains(" : ")) {
            String[] parts = title.split("\\s:\\s", 2);
            if (parts.length == 2 && looksLikeArtistBlock(parts[1], channel)) {
                title = parts[0];
            }
        }

        if (title.contains(" - ")) {
            String[] parts = title.split("\\s-\\s", 2);
            if (parts.length == 2) {
                if (looksLikeArtistBlock(parts[0], channel) && !parts[1].isBlank()) {
                    title = parts[1];
                } else if (looksLikeArtistBlock(parts[1], channel) && !parts[0].isBlank()) {
                    title = parts[0];
                }
            }
        }

        title = title.replaceAll("\\s{2,}", " ").trim();
        if (title.isBlank()) {
            return rawTitle.trim();
        }
        return title;
    }

    private boolean looksLikeArtistBlock(String text, String channel) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String t = normalizeToken(text);
        String ch = normalizeToken(channel);
        if (!ch.isBlank() && t.contains(ch)) {
            return true;
        }
        return t.matches(".*\\b(ft|feat|featuring|prod|records|music|official|tseries|yrf|saregama)\\b.*");
    }

    private String normalizeToken(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s{2,}", " ").trim();
    }
}
