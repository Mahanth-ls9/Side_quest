package com.audiodownloader.service;

import com.audiodownloader.config.AppProperties;
import com.audiodownloader.metadata.TrackMetadata;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Service
public class MetadataService {
    private final AcoustIdService acoustIdService;
    private final AcoustIdLookupQueue acoustIdLookupQueue;
    private final MusicBrainzService musicBrainzService;
    private final CoverArtService coverArtService;
    private final AppProperties appProperties;

    public MetadataService(AcoustIdService acoustIdService,
                           AcoustIdLookupQueue acoustIdLookupQueue,
                           MusicBrainzService musicBrainzService,
                           CoverArtService coverArtService,
                           AppProperties appProperties) {
        this.acoustIdService = acoustIdService;
        this.acoustIdLookupQueue = acoustIdLookupQueue;
        this.musicBrainzService = musicBrainzService;
        this.coverArtService = coverArtService;
        this.appProperties = appProperties;
    }

    public TrackMetadata enrichMetadata(Path downloadedFile, TrackMetadata baseMetadata) {
        return mergeWithOnlineMetadata(downloadedFile, baseMetadata);
    }

    public Path tagAndOrganize(Path downloadedFile, TrackMetadata metadata) {
        applyTags(downloadedFile, metadata);
        return organizeFile(downloadedFile, metadata);
    }

    private TrackMetadata mergeWithOnlineMetadata(Path downloadedFile, TrackMetadata baseMetadata) {
        TrackMetadata result = new TrackMetadata();
        result.setTitle(baseMetadata.getTitle());
        result.setArtist(baseMetadata.getArtist());
        result.setAlbum(baseMetadata.getAlbum());

        AcoustIdService.FingerprintResult fingerprintResult = acoustIdService.fingerprint(downloadedFile);
        if (fingerprintResult != null) {
            result.setFingerprint(fingerprintResult.fingerprint());
            String recordingId = lookupRecordingIdWithRateLimit(fingerprintResult);
            MusicBrainzService.MusicBrainzResult mbResult = musicBrainzService.lookupRecording(recordingId);
            if (mbResult != null && mbResult.metadata() != null) {
                TrackMetadata mb = mbResult.metadata();
                if (StringUtils.isNotBlank(mb.getTitle())) {
                    result.setTitle(mb.getTitle());
                }
                if (StringUtils.isNotBlank(mb.getArtist())) {
                    result.setArtist(mb.getArtist());
                }
                if (StringUtils.isNotBlank(mb.getAlbum())) {
                    result.setAlbum(mb.getAlbum());
                }
                result.setAlbumArtist(mb.getAlbumArtist());
                result.setYear(mb.getYear());
                result.setGenre(mb.getGenre());
                result.setTrackNumber(mb.getTrackNumber());
                result.setArtworkBytes(coverArtService.fetchFrontCover(mbResult.releaseId()));
            }
        }

        if (StringUtils.isBlank(result.getArtist())) {
            result.setArtist("Unknown Artist");
        }
        if (StringUtils.isBlank(result.getAlbum())) {
            result.setAlbum("Unknown Album");
        }
        if (StringUtils.isBlank(result.getTitle())) {
            result.setTitle(downloadedFile.getFileName().toString().replace(".m4a", ""));
        }
        if (StringUtils.isBlank(result.getAlbumArtist())) {
            result.setAlbumArtist(result.getArtist());
        }
        if (StringUtils.isBlank(result.getTrackNumber())) {
            result.setTrackNumber("0");
        }
        return result;
    }

    private String lookupRecordingIdWithRateLimit(AcoustIdService.FingerprintResult fingerprintResult) {
        try {
            return acoustIdLookupQueue.enqueueLookup(fingerprintResult).get(20, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            return null;
        }
    }

    private void applyTags(Path file, TrackMetadata metadata) {
        try {
            Path tempAudio = Files.createTempFile("audio-downloader-tagged-", ".m4a");
            List<String> command = new ArrayList<>();
            command.add(appProperties.getFfmpegCommand());
            command.add("-y");
            command.add("-i");
            command.add(file.toString());

            Path artworkPath = null;
            if (metadata.getArtworkBytes() != null) {
                artworkPath = Files.createTempFile("audio-downloader-cover-", ".jpg");
                Files.write(artworkPath, metadata.getArtworkBytes());
                command.add("-i");
                command.add(artworkPath.toString());
            }

            command.add("-map");
            command.add("0:a");
            if (artworkPath != null) {
                command.add("-map");
                command.add("1:v");
                command.add("-disposition:v");
                command.add("attached_pic");
            }
            command.add("-c");
            command.add("copy");
            addMetadata(command, "title", metadata.getTitle());
            addMetadata(command, "artist", metadata.getArtist());
            addMetadata(command, "album", metadata.getAlbum());
            addMetadata(command, "album_artist", metadata.getAlbumArtist());
            addMetadata(command, "track", metadata.getTrackNumber());
            addMetadata(command, "date", defaultString(metadata.getYear()));
            addMetadata(command, "genre", defaultString(metadata.getGenre()));
            command.add(tempAudio.toString());

            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            int exit = process.waitFor();
            if (exit == 0) {
                Files.move(tempAudio, file, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.deleteIfExists(tempAudio);
            }
            if (artworkPath != null) {
                Files.deleteIfExists(artworkPath);
            }
        } catch (Exception ignored) {
        }
    }

    private void addMetadata(List<String> command, String key, String value) {
        if (!StringUtils.isBlank(value)) {
            command.add("-metadata");
            command.add(key + "=" + value);
        }
    }

    private Path organizeFile(Path sourceFile, TrackMetadata metadata) {
        try {
            String artist = sanitize(defaultString(metadata.getArtist(), "Unknown Artist"));
            String album = sanitize(defaultString(metadata.getAlbum(), "Unknown Album"));
            String track = String.format("%02d", parseTrack(metadata.getTrackNumber()));
            String title = sanitize(defaultString(metadata.getTitle(), sourceFile.getFileName().toString().replace(".m4a", "")));

            Path targetDir = Path.of(appProperties.getMusicFolder(), artist, album);
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(track + " " + title + ".m4a");
            Files.move(sourceFile, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (Exception e) {
            return sourceFile;
        }
    }

    private int parseTrack(String trackNumber) {
        try {
            String normalized = defaultString(trackNumber, "0").split("/")[0].trim();
            return Integer.parseInt(normalized);
        } catch (Exception e) {
            return 0;
        }
    }

    private String sanitize(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|]", "").trim();
    }

    private String defaultString(String value) {
        return defaultString(value, "");
    }

    private String defaultString(String value, String fallback) {
        return StringUtils.isBlank(value) ? fallback : value;
    }
}
