package com.audiodownloader.service;

import com.audiodownloader.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

@Service
public class AcoustIdService {
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public AcoustIdService(AppProperties appProperties, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    public FingerprintResult fingerprint(Path audioFile) {
        try {
            Process process = new ProcessBuilder(appProperties.getFpcalcCommand(), "-json", audioFile.toString())
                    .redirectErrorStream(true)
                    .start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }
            if (process.waitFor() != 0) {
                return null;
            }
            JsonNode node = objectMapper.readTree(output.toString());
            return new FingerprintResult(node.path("fingerprint").asText(), node.path("duration").asInt());
        } catch (Exception e) {
            return null;
        }
    }

    String rawLookupMusicBrainzRecordingId(FingerprintResult fingerprintResult) {
        if (fingerprintResult == null || appProperties.getAcoustIdApiKey().isBlank()) {
            return null;
        }
        try {
            String query = "https://api.acoustid.org/v2/lookup?client="
                    + URLEncoder.encode(appProperties.getAcoustIdApiKey(), StandardCharsets.UTF_8)
                    + "&meta=recordings"
                    + "&duration=" + fingerprintResult.duration()
                    + "&fingerprint=" + URLEncoder.encode(fingerprintResult.fingerprint(), StandardCharsets.UTF_8)
                    + "&format=json";
            HttpRequest request = HttpRequest.newBuilder(URI.create(query)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode results = root.path("results");
            if (results.isArray() && !results.isEmpty()) {
                JsonNode recordings = results.get(0).path("recordings");
                if (recordings.isArray() && !recordings.isEmpty()) {
                    return recordings.get(0).path("id").asText(null);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public record FingerprintResult(String fingerprint, int duration) {
    }
}
