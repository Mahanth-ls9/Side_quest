package com.audiodownloader.library;

import com.audiodownloader.metadata.TrackMetadata;
import org.springframework.stereotype.Service;

@Service
public class DuplicateDetector {
    private final LibraryIndexService libraryIndexService;

    public DuplicateDetector(LibraryIndexService libraryIndexService) {
        this.libraryIndexService = libraryIndexService;
    }

    public boolean isDuplicate(String outputFileName, TrackMetadata metadata) {
        if (!libraryIndexService.findByFileName(outputFileName).isEmpty()) {
            return true;
        }
        if (metadata.getTitle() != null && metadata.getArtist() != null && metadata.getAlbum() != null
                && !libraryIndexService.findByMetadata(metadata.getTitle(), metadata.getArtist(), metadata.getAlbum()).isEmpty()) {
            return true;
        }
        return metadata.getFingerprint() != null && !metadata.getFingerprint().isBlank()
                && !libraryIndexService.findByFingerprint(metadata.getFingerprint()).isEmpty();
    }
}
