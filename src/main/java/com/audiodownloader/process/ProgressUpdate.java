package com.audiodownloader.process;

public class ProgressUpdate {
    private final double progressPercent;
    private final String speed;
    private final String eta;
    private final String rawLine;

    public ProgressUpdate(double progressPercent, String speed, String eta, String rawLine) {
        this.progressPercent = progressPercent;
        this.speed = speed;
        this.eta = eta;
        this.rawLine = rawLine;
    }

    public double getProgressPercent() {
        return progressPercent;
    }

    public String getSpeed() {
        return speed;
    }

    public String getEta() {
        return eta;
    }

    public String getRawLine() {
        return rawLine;
    }
}
