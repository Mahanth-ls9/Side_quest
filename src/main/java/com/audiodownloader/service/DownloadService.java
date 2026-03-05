package com.audiodownloader.service;

import com.audiodownloader.config.AppProperties;
import com.audiodownloader.metadata.TrackMetadata;
import com.audiodownloader.process.ProcessService;
import com.audiodownloader.process.ProgressParser;
import com.audiodownloader.process.ProgressUpdate;
import com.audiodownloader.ui.model.TrackInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DownloadService {
    private static final Pattern DESTINATION_PATTERN = Pattern.compile("Destination:\\s+(.+)$");

    private final AppProperties properties;
    private final ProcessService processService;
    private final ProgressParser progressParser;
    private final ObjectMapper objectMapper;

    public DownloadService(AppProperties properties,
                           ProcessService processService,
                           ProgressParser progressParser,
                           ObjectMapper objectMapper) {
        this.properties = properties;
        this.processService = processService;
        this.progressParser = progressParser;
        this.objectMapper = objectMapper;
    }

    public List<TrackInfo> fetchPlaylist(String url) {
        List<TrackInfo> tracks = new ArrayList<>();
        List<String> command = List.of(
                properties.getYtDlpCommand(),
                "--dump-single-json",
                "--flat-playlist",
                "--no-warnings",
                url
        );
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            StringBuilder jsonOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonOutput.append(line);
                }
            }
            if (process.waitFor() != 0) {
                return tracks;
            }
            JsonNode root = objectMapper.readTree(jsonOutput.toString());
            JsonNode entries = root.path("entries");
            if (entries.isArray() && !entries.isEmpty()) {
                for (JsonNode entry : entries) {
                    TrackInfo track = new TrackInfo();
                    track.setId(entry.path("id").asText(UUID.randomUUID().toString()));
                    track.setTitle(entry.path("title").asText("Unknown Title"));
                    track.setDuration(formatDuration(entry.path("duration").asInt(0)));
                    track.setChannel(entry.path("channel").asText(entry.path("uploader").asText("Unknown Channel")));
                    track.setSourceUrl("https://www.youtube.com/watch?v=" + entry.path("id").asText());
                    tracks.add(track);
                }
            } else {
                TrackInfo single = new TrackInfo();
                single.setId(root.path("id").asText(UUID.randomUUID().toString()));
                single.setTitle(root.path("title").asText("Unknown Title"));
                single.setDuration(formatDuration(root.path("duration").asInt(0)));
                single.setChannel(root.path("channel").asText(root.path("uploader").asText("Unknown Channel")));
                single.setSourceUrl(url);
                tracks.add(single);
            }
        } catch (Exception ignored) {
        }
        return tracks;
    }

    public DownloadResult downloadTrack(TrackInfo trackInfo,
                                        Consumer<ProgressUpdate> progressConsumer,
                                        Consumer<String> logConsumer) {
        String processId = "dl-" + trackInfo.getId();
        DownloadState state = new DownloadState();
        Path stagingDir = resolveStagingDirectory();

        List<String> command = new ArrayList<>();
        command.add(properties.getYtDlpCommand());
        command.add("-f");
        command.add("bestaudio[abr>=128]/bestaudio/best");
        command.add("-N");
        command.add(String.valueOf(properties.getMaxConcurrentDownloads()));
        command.add("--extract-audio");
        command.add("--audio-format");
        command.add("m4a");
        command.add("--audio-quality");
        command.add("0");
        command.add("--embed-metadata");
        command.add("--embed-thumbnail");
        command.add("--convert-thumbnails");
        command.add("jpg");
        command.add("--restrict-filenames");
        command.add("--windows-filenames");
        command.add("--trim-filenames");
        command.add("120");
        command.add("--yes-playlist");
        command.add("--no-overwrites");
        command.add("--continue");
        command.add("--replace-in-metadata");
        command.add("title");
        command.add("^.*? - ");
        command.add("");
        command.add("--replace-in-metadata");
        command.add("title");
        command.add("\\s*\\(.*?\\)");
        command.add("");
        command.add("-P");
        command.add(stagingDir.toString());
        command.add("-o");
        command.add("%(title)s.%(ext)s");
        command.add(trackInfo.getSourceUrl());

        try {
            int exit = processService.runCommand(processId, command, stagingDir, line -> {
                logConsumer.accept(line);
                Optional<ProgressUpdate> update = progressParser.parse(line);
                update.ifPresent(progressConsumer);
                extractDestination(line).ifPresent(path -> state.downloadedFile = toAbsolutePath(path, stagingDir));
            });
            if (exit != 0) {
                return DownloadResult.failed("yt-dlp failed with exit code " + exit);
            }
            TrackMetadata metadata = new TrackMetadata();
            metadata.setTitle(trackInfo.getTitle());
            metadata.setArtist(trackInfo.getChannel());
            return DownloadResult.completed(state.downloadedFile, metadata);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return DownloadResult.failed(e.getMessage());
        } catch (IOException e) {
            return DownloadResult.failed(e.getMessage());
        }
    }

    public void cancelTrack(String trackId) {
        processService.cancel("dl-" + trackId);
    }

    private Optional<String> extractDestination(String line) {
        Matcher matcher = DESTINATION_PATTERN.matcher(line);
        if (matcher.find()) {
            return Optional.of(matcher.group(1).trim());
        }
        return Optional.empty();
    }

    private Path toAbsolutePath(String rawPath, Path stagingDir) {
        Path parsed = Path.of(rawPath);
        if (parsed.isAbsolute()) {
            return parsed;
        }
        return stagingDir.resolve(parsed).normalize();
    }

    private Path resolveStagingDirectory() {
        Path configuredMusic = Path.of(properties.getMusicFolder());
        Path staging = configuredMusic.resolve(".incoming");
        try {
            Files.createDirectories(staging);
            return staging;
        } catch (IOException e) {
            Path fallback = Path.of(System.getProperty("user.home"), ".audio-downloader", "incoming");
            try {
                Files.createDirectories(fallback);
            } catch (IOException ignored) {
            }
            return fallback;
        }
    }

    private String formatDuration(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return "%d:%02d".formatted(minutes, seconds);
    }

    private static class DownloadState {
        private Path downloadedFile;
    }

    public record DownloadResult(boolean success, Path downloadedFile, TrackMetadata baseMetadata, String error) {
        public static DownloadResult completed(Path file, TrackMetadata metadata) {
            return new DownloadResult(true, file, metadata, "");
        }

        public static DownloadResult failed(String error) {
            return new DownloadResult(false, null, null, error);
        }
    }
}
