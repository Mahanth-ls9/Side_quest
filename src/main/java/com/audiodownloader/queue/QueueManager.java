package com.audiodownloader.queue;

import com.audiodownloader.config.AppProperties;
import com.audiodownloader.library.DuplicateDetector;
import com.audiodownloader.library.LibraryIndexService;
import com.audiodownloader.metadata.TrackMetadata;
import com.audiodownloader.service.DownloadService;
import com.audiodownloader.service.MetadataService;
import com.audiodownloader.ui.model.DownloadStatus;
import com.audiodownloader.ui.model.QueueItem;
import com.audiodownloader.ui.model.TrackInfo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class QueueManager {
    private final DownloadService downloadService;
    private final MetadataService metadataService;
    private final DuplicateDetector duplicateDetector;
    private final LibraryIndexService libraryIndexService;
    private final ExecutorService executorService;
    private final List<QueueItem> queueItems = new CopyOnWriteArrayList<>();
    private final Map<String, Future<?>> runningTasks = new ConcurrentHashMap<>();
    private final List<QueueEventListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean paused = new AtomicBoolean(false);

    public QueueManager(DownloadService downloadService,
                        MetadataService metadataService,
                        DuplicateDetector duplicateDetector,
                        LibraryIndexService libraryIndexService,
                        AppProperties appProperties) {
        this.downloadService = downloadService;
        this.metadataService = metadataService;
        this.duplicateDetector = duplicateDetector;
        this.libraryIndexService = libraryIndexService;
        this.executorService = Executors.newFixedThreadPool(appProperties.getMaxConcurrentDownloads());
    }

    public void addListener(QueueEventListener listener) {
        listeners.add(listener);
    }

    public List<QueueItem> getQueueItems() {
        return new ArrayList<>(queueItems);
    }

    public void enqueue(TrackInfo trackInfo) {
        QueueItem queueItem = new QueueItem(trackInfo);
        queueItem.setStatus(DownloadStatus.WAITING);
        queueItems.add(queueItem);
        fireUpdate(queueItem);
        schedule(queueItem);
    }

    public void pauseAll() {
        paused.set(true);
        for (QueueItem item : queueItems) {
            if (item.getStatus() == DownloadStatus.DOWNLOADING || item.getStatus() == DownloadStatus.WAITING) {
                item.setStatus(DownloadStatus.PAUSED);
                downloadService.cancelTrack(item.getTrackInfo().getId());
                fireUpdate(item);
            }
        }
    }

    public void resumeAll() {
        paused.set(false);
        for (QueueItem item : queueItems) {
            if (item.getStatus() == DownloadStatus.PAUSED) {
                item.setStatus(DownloadStatus.WAITING);
                fireUpdate(item);
                schedule(item);
            }
        }
    }

    public void cancel(String trackId) {
        queueItems.stream()
                .filter(item -> item.getTrackInfo().getId().equals(trackId))
                .findFirst()
                .ifPresent(item -> {
                    item.setStatus(DownloadStatus.CANCELED);
                    downloadService.cancelTrack(trackId);
                    Future<?> task = runningTasks.remove(trackId);
                    if (task != null) {
                        task.cancel(true);
                    }
                    fireUpdate(item);
                    fireCompleted(item);
                });
    }

    public void clearFinished() {
        queueItems.removeIf(item ->
                item.getStatus() == DownloadStatus.COMPLETED ||
                        item.getStatus() == DownloadStatus.FAILED ||
                        item.getStatus() == DownloadStatus.CANCELED ||
                        item.getStatus() == DownloadStatus.DUPLICATE);
    }

    private void schedule(QueueItem item) {
        Future<?> future = executorService.submit(() -> processQueueItem(item));
        runningTasks.put(item.getTrackInfo().getId(), future);
    }

    private void processQueueItem(QueueItem item) {
        if (paused.get()) {
            return;
        }
        try {
            item.setStatus(DownloadStatus.DOWNLOADING);
            fireUpdate(item);

            DownloadService.DownloadResult result = downloadService.downloadTrack(item.getTrackInfo(), update -> {
                item.setProgress(update.getProgressPercent() / 100.0);
                item.setSpeed(update.getSpeed());
                item.setEta(update.getEta());
                fireUpdate(item);
            }, this::fireLog);

            if (!result.success()) {
                if (item.getStatus() != DownloadStatus.CANCELED) {
                    item.setStatus(DownloadStatus.FAILED);
                    item.setErrorMessage(result.error());
                    fireUpdate(item);
                }
                fireCompleted(item);
                return;
            }

            if (result.downloadedFile() == null) {
                item.setStatus(DownloadStatus.FAILED);
                item.setErrorMessage("Downloaded file path not found from yt-dlp output");
                fireUpdate(item);
                fireCompleted(item);
                return;
            }

            TrackMetadata metadata = metadataService.enrichMetadata(result.downloadedFile(), result.baseMetadata());
            if (duplicateDetector.isDuplicate(result.downloadedFile().getFileName().toString(), metadata)) {
                item.setStatus(DownloadStatus.DUPLICATE);
            } else {
                var finalPath = metadataService.tagAndOrganize(result.downloadedFile(), metadata);
                libraryIndexService.indexFile(finalPath, metadata);
                item.setStatus(DownloadStatus.COMPLETED);
                item.setProgress(1.0);
            }
            fireUpdate(item);
            fireCompleted(item);
        } catch (Exception e) {
            item.setStatus(DownloadStatus.FAILED);
            item.setErrorMessage(e.getMessage());
            fireUpdate(item);
            fireCompleted(item);
        } finally {
            runningTasks.remove(item.getTrackInfo().getId());
        }
    }

    private void fireUpdate(QueueItem item) {
        for (QueueEventListener listener : listeners) {
            listener.onQueueItemUpdated(item);
        }
    }

    private void fireCompleted(QueueItem item) {
        for (QueueEventListener listener : listeners) {
            listener.onQueueItemCompleted(item);
        }
    }

    private void fireLog(String message) {
        for (QueueEventListener listener : listeners) {
            listener.onLog(message);
        }
    }
}
