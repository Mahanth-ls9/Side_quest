# Audio Downloader Pro

Cross-platform desktop music downloader + library manager built with Java 21, Spring Boot, JavaFX, and Docker.

## Features

- Native desktop GUI (JavaFX)
- Paste YouTube video/playlist URL
- Playlist preview with track selection
- Download queue with max 4 concurrent downloads
- Multi-progress rows, speed and ETA
- Real-time log console
- Pause/resume/cancel queue items
- Metadata enrichment pipeline:
  - yt-dlp metadata
  - chromaprint/AcoustID fingerprint
  - dedicated fingerprint queue + `RateLimitedAcoustIdWorker` (max `3 req/sec` globally)
  - MusicBrainz lookup
  - Cover Art Archive artwork
  - jaudiotagger tag writing
- Smart duplicate detection:
  - filename
  - metadata
  - fingerprint
- Automatic library organization:
  - `Music/Artist/Album/01 Track.m4a`
- Startup library indexing in SQLite:
  - `~/.audio-downloader/library.db`
- Download history:
  - `~/.audio-downloader/history.log`
- Optional enhancements included:
  - drag-drop URL input
  - clipboard auto-detection for YouTube links

## Project Structure

```text
audio-downloader/
 ├─ src/main/java/com/audiodownloader/
 │   ├─ config/
 │   ├─ queue/
 │   ├─ service/
 │   ├─ metadata/
 │   ├─ process/
 │   ├─ library/
 │   └─ ui/
 ├─ src/main/resources/
 │   ├─ layout.fxml
 │   ├─ styles.css
 │   └─ icons/
 ├─ docker/
 │   ├─ Dockerfile
 │   └─ build.sh
 ├─ dist/
 ├─ pom.xml
 └─ README.md
```

## Requirements

- Java 21
- Maven 3.9+
- `yt-dlp`
- `ffmpeg`
- `fpcalc` (chromaprint)
- Optional: `ACOUSTID_API_KEY`

## Local Run

```bash
mvn spring-boot:run
```

## Docker Build Environment

Build the Docker image:

```bash
docker build -t audio-downloader-pro-builder -f docker/Dockerfile .
```

Produce a Linux app-image:

```bash
docker run --rm -e INSTALLER_TYPE=app-image -v "$(pwd)/dist:/dist" audio-downloader-pro-builder
```

Try platform installer types:

```bash
docker run --rm -e INSTALLER_TYPE=exe -v "$(pwd)/dist:/dist" audio-downloader-pro-builder
docker run --rm -e INSTALLER_TYPE=dmg -v "$(pwd)/dist:/dist" audio-downloader-pro-builder
```

`jpackage` generally requires platform-native packaging tools for `.exe` and `.dmg`, so those types should run in matching CI runners/containers for their target OS.

## Configuration

`src/main/resources/application.yml`

```yaml
audio:
  downloader:
    music-folder: ${user.home}/Music
    max-concurrent-downloads: 4
    yt-dlp-command: yt-dlp
    ffmpeg-command: ffmpeg
    fpcalc-command: fpcalc
    acoust-id-api-key: ${ACOUSTID_API_KEY:}
```

## Notes

- Duplicate tracks are marked in playlist preview and skipped during queue processing.
- Pause uses graceful cancel + resume (`yt-dlp --continue`) behavior.
- Fallback organization path is `Unknown Artist/Unknown Album`.
