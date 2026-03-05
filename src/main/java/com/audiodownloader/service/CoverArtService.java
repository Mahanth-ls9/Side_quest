package com.audiodownloader.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class CoverArtService {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;

    public CoverArtService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] fetchFrontCover(String releaseId) {
        if (releaseId == null || releaseId.isBlank()) {
            return null;
        }
        try {
            HttpRequest lookupRequest = HttpRequest.newBuilder(
                            URI.create("https://coverartarchive.org/release/" + releaseId))
                    .header("User-Agent", "AudioDownloaderPro/1.0 (https://example.local)")
                    .GET()
                    .build();
            HttpResponse<String> lookupResponse = httpClient.send(lookupRequest, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(lookupResponse.body());
            JsonNode images = root.path("images");
            if (images.isArray() && !images.isEmpty()) {
                String imageUrl = images.get(0).path("image").asText(null);
                if (imageUrl != null) {
                    HttpRequest imageRequest = HttpRequest.newBuilder(URI.create(imageUrl)).GET().build();
                    HttpResponse<byte[]> imageResponse = httpClient.send(imageRequest, HttpResponse.BodyHandlers.ofByteArray());
                    return imageResponse.body();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
