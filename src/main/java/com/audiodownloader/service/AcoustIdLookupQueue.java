package com.audiodownloader.service;

import com.google.common.util.concurrent.RateLimiter;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;

@Service
public class AcoustIdLookupQueue {
    private final AcoustIdService acoustIdService;
    private final RateLimiter limiter = RateLimiter.create(3.0);
    private final BlockingQueue<LookupTask> queue = new LinkedBlockingQueue<>();
    private ExecutorService worker;
    private volatile boolean running;

    public AcoustIdLookupQueue(AcoustIdService acoustIdService) {
        this.acoustIdService = acoustIdService;
    }

    @PostConstruct
    public void start() {
        running = true;
        worker = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "RateLimitedAcoustIdWorker");
            thread.setDaemon(true);
            return thread;
        });
        worker.submit(this::runLoop);
    }

    public CompletableFuture<String> enqueueLookup(AcoustIdService.FingerprintResult fingerprintResult) {
        CompletableFuture<String> result = new CompletableFuture<>();
        if (fingerprintResult == null) {
            result.complete(null);
            return result;
        }
        queue.offer(new LookupTask(fingerprintResult, result));
        return result;
    }

    private void runLoop() {
        while (running) {
            try {
                LookupTask task = queue.poll(250, TimeUnit.MILLISECONDS);
                if (task == null) {
                    continue;
                }
                try {
                    limiter.acquire();
                    String recordingId = acoustIdService.rawLookupMusicBrainzRecordingId(task.fingerprintResult());
                    task.responseFuture().complete(recordingId);
                } catch (Exception e) {
                    task.responseFuture().complete(null);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        if (worker != null) {
            worker.shutdownNow();
        }
    }

    private record LookupTask(AcoustIdService.FingerprintResult fingerprintResult,
                              CompletableFuture<String> responseFuture) {
    }
}
