package com.audiodownloader.service;

import com.audiodownloader.ui.model.QueueItem;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class DownloadHistoryService {
    private final Path historyPath = Path.of(System.getProperty("user.home"), ".audio-downloader", "history.log");

    public synchronized void append(QueueItem item) {
        try {
            Files.createDirectories(historyPath.getParent());
            String content = item.getTrackInfo().getTitle() + "\t" + item.getStatus();
            String line = LocalDateTime.now() + "\t" + content + System.lineSeparator();
            if (Files.exists(historyPath)) {
                List<String> lines = Files.readAllLines(historyPath);
                if (!lines.isEmpty()) {
                    String last = lines.get(lines.size() - 1);
                    String[] parts = last.split("\t", 2);
                    if (parts.length == 2 && parts[1].equals(content)) {
                        return;
                    }
                }
            }
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
            int from = Math.max(0, lines.size() - Math.max(max * 4, max));
            List<String> recentWindow = lines.subList(from, lines.size());
            Set<String> unique = new LinkedHashSet<>();
            for (int i = recentWindow.size() - 1; i >= 0; i--) {
                String line = recentWindow.get(i);
                String[] parts = line.split("\t", 3);
                if (parts.length == 3) {
                    unique.add(parts[1] + " - " + parts[2]);
                } else {
                    unique.add(line);
                }
                if (unique.size() >= max) {
                    break;
                }
            }
            List<String> deduped = new ArrayList<>(unique);
            java.util.Collections.reverse(deduped);
            return deduped;
        } catch (Exception e) {
            return List.of();
        }
    }
}
