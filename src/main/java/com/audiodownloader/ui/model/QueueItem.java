package com.audiodownloader.ui.model;

import java.nio.file.Path;

public class QueueItem {
    private final TrackInfo trackInfo;
    private DownloadStatus status = DownloadStatus.WAITING;
    private double progress;
    private String speed = "-";
    private String eta = "-";
    private Path downloadedFile;
    private String errorMessage = "";

    public QueueItem(TrackInfo trackInfo) {
        this.trackInfo = trackInfo;
    }

    public TrackInfo getTrackInfo() {
        return trackInfo;
    }

    public DownloadStatus getStatus() {
        return status;
    }

    public void setStatus(DownloadStatus status) {
        this.status = status;
    }

    public double getProgress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = progress;
    }

    public String getSpeed() {
        return speed;
    }

    public void setSpeed(String speed) {
        this.speed = speed;
    }

    public String getEta() {
        return eta;
    }

    public void setEta(String eta) {
        this.eta = eta;
    }

    public Path getDownloadedFile() {
        return downloadedFile;
    }

    public void setDownloadedFile(Path downloadedFile) {
        this.downloadedFile = downloadedFile;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
