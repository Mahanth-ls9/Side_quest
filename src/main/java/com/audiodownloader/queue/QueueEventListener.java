package com.audiodownloader.queue;

import com.audiodownloader.ui.model.QueueItem;

public interface QueueEventListener {
    void onQueueItemUpdated(QueueItem item);

    void onQueueItemCompleted(QueueItem item);

    void onLog(String message);
}
