package com.audiodownloader.service;

import com.audiodownloader.metadata.TrackMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class MusicBrainzService {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public MusicBrainzService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public MusicBrainzResult lookupRecording(String recordingId) {
        if (recordingId == null || recordingId.isBlank()) {
            return null;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            "https://musicbrainz.org/ws/2/recording/" + recordingId + "?fmt=json&inc=artists+releases+genres"))
                    .header("User-Agent", "AudioDownloaderPro/1.0 (https://example.local)")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());

            TrackMetadata metadata = new TrackMetadata();
            metadata.setTitle(root.path("title").asText(""));
            JsonNode artists = root.path("artist-credit");
            if (artists.isArray() && !artists.isEmpty()) {
                metadata.setArtist(artists.get(0).path("name").asText(""));
                metadata.setAlbumArtist(artists.get(0).path("name").asText(""));
            }
            JsonNode releases = root.path("releases");
            String releaseId = null;
            if (releases.isArray() && !releases.isEmpty()) {
                JsonNode release = releases.get(0);
                metadata.setAlbum(release.path("title").asText(""));
                metadata.setYear(release.path("date").asText("").split("-")[0]);
                releaseId = release.path("id").asText(null);
            }
            JsonNode genres = root.path("genres");
            if (genres.isArray() && !genres.isEmpty()) {
                metadata.setGenre(genres.get(0).path("name").asText(""));
            }
            return new MusicBrainzResult(metadata, releaseId);
        } catch (Exception e) {
            return null;
        }
    }

    public record MusicBrainzResult(TrackMetadata metadata, String releaseId) {
    }
}
