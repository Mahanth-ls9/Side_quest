package com.audiodownloader.service;

import com.audiodownloader.ui.model.QueueItem;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class DownloadHistoryService {
    private final Path historyPath = Path.of(System.getProperty("user.home"), ".audio-downloader", "history.log");

    public synchronized void append(QueueItem item) {
        try {
            Files.createDirectories(historyPath.getParent());
            String line = LocalDateTime.now() + "\t" + item.getTrackInfo().getTitle() + "\t" + item.getStatus() + System.lineSeparator();
            Files.writeString(historyPath, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    public synchronized List<String> loadRecent(int max) {
        try {
            if (!Files.exists(historyPath)) {
                return List.of();
            }
            List<String> lines = Files.readAllLines(historyPath);
            int from = Math.max(0, lines.size() - max);
            return new ArrayList<>(lines.subList(from, lines.size()));
        } catch (Exception e) {
            return List.of();
        }
    }
}
