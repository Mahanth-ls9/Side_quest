package com.audiodownloader.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "audio.downloader")
public class AppProperties {
    private String musicFolder = System.getProperty("user.home") + "/Music";
    private int maxConcurrentDownloads = 4;
    private String ytDlpCommand = "yt-dlp";
    private String ffmpegCommand = "ffmpeg";
    private String fpcalcCommand = "fpcalc";
    private String acoustIdApiKey = "";

    public String getMusicFolder() {
        return musicFolder;
    }

    public void setMusicFolder(String musicFolder) {
        this.musicFolder = musicFolder;
    }

    public int getMaxConcurrentDownloads() {
        return maxConcurrentDownloads;
    }

    public void setMaxConcurrentDownloads(int maxConcurrentDownloads) {
        this.maxConcurrentDownloads = maxConcurrentDownloads;
    }

    public String getYtDlpCommand() {
        return ytDlpCommand;
    }

    public void setYtDlpCommand(String ytDlpCommand) {
        this.ytDlpCommand = ytDlpCommand;
    }

    public String getFfmpegCommand() {
        return ffmpegCommand;
    }

    public void setFfmpegCommand(String ffmpegCommand) {
        this.ffmpegCommand = ffmpegCommand;
    }

    public String getFpcalcCommand() {
        return fpcalcCommand;
    }

    public void setFpcalcCommand(String fpcalcCommand) {
        this.fpcalcCommand = fpcalcCommand;
    }

    public String getAcoustIdApiKey() {
        return acoustIdApiKey;
    }

    public void setAcoustIdApiKey(String acoustIdApiKey) {
        this.acoustIdApiKey = acoustIdApiKey;
    }
}
