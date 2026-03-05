package com.audiodownloader.library;

import com.audiodownloader.config.AppProperties;
import com.audiodownloader.metadata.TrackMetadata;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
public class LibraryIndexService {
    private final Path dbPath;
    private final AppProperties appProperties;

    public LibraryIndexService(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.dbPath = Paths.get(System.getProperty("user.home"), ".audio-downloader", "library.db");
        initDb();
    }

    public void initDb() {
        try {
            Files.createDirectories(dbPath.getParent());
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
                try (Statement statement = connection.createStatement()) {
                    String ddl = "CREATE TABLE IF NOT EXISTS library_index ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                            + "title TEXT,"
                            + "artist TEXT,"
                            + "album TEXT,"
                            + "track_number TEXT,"
                            + "fingerprint TEXT,"
                            + "file_path TEXT UNIQUE"
                            + ")";
                    statement.execute(ddl);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void indexFile(Path file, TrackMetadata metadata) {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            String sql = "INSERT OR REPLACE INTO library_index(title, artist, album, track_number, fingerprint, file_path) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, metadata.getTitle());
                ps.setString(2, metadata.getArtist());
                ps.setString(3, metadata.getAlbum());
                ps.setString(4, metadata.getTrackNumber());
                ps.setString(5, metadata.getFingerprint());
                ps.setString(6, file.toAbsolutePath().toString());
                ps.executeUpdate();
            }
        } catch (Exception ignored) {
        }
    }

    public List<LibraryRecord> findByFingerprint(String fingerprint) {
        return find("SELECT title, artist, album, track_number, fingerprint, file_path FROM library_index WHERE fingerprint = ?",
                fingerprint);
    }

    public List<LibraryRecord> findByMetadata(String title, String artist, String album) {
        if (isBlank(title) || isBlank(artist) || isBlank(album)) {
            return new ArrayList<>();
        }
        String sql = "SELECT title, artist, album, track_number, fingerprint, file_path "
                + "FROM library_index "
                + "WHERE lower(title)=lower(?) AND lower(artist)=lower(?) AND lower(album)=lower(?)";
        return find(sql, title, artist, album);
    }

    public List<LibraryRecord> findByFileName(String fileName) {
        if (isBlank(fileName)) {
            return new ArrayList<>();
        }
        return find("SELECT title, artist, album, track_number, fingerprint, file_path FROM library_index WHERE file_path LIKE ?",
                "%" + fileName);
    }

    public void startupScan() {
        Path music = Path.of(appProperties.getMusicFolder());
        if (!Files.isDirectory(music)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(music)) {
            stream
                    .filter(p -> p.toString().toLowerCase().endsWith(".m4a"))
                    .forEach(this::indexFromFileTags);
        } catch (Exception ignored) {
        }
    }

    private void indexFromFileTags(Path file) {
        try {
            Metadata metadataReader = ImageMetadataReader.readMetadata(file.toFile());
            TrackMetadata metadata = new TrackMetadata();
            metadata.setTitle(readTag(metadataReader, "Title"));
            metadata.setArtist(readTag(metadataReader, "Artist"));
            metadata.setAlbum(readTag(metadataReader, "Album"));
            metadata.setTrackNumber(readTag(metadataReader, "Track"));
            metadata.setFingerprint(null);
            indexFile(file, metadata);
        } catch (Exception ignored) {
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String readTag(Metadata metadata, String expectedName) {
        for (var directory : metadata.getDirectories()) {
            for (Tag tag : directory.getTags()) {
                if (tag.getTagName().equalsIgnoreCase(expectedName)) {
                    return tag.getDescription();
                }
            }
        }
        return "";
    }

    private List<LibraryRecord> find(String sql, String... values) {
        List<LibraryRecord> results = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                ps.setString(i + 1, values[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LibraryRecord record = new LibraryRecord();
                    record.setTitle(rs.getString("title"));
                    record.setArtist(rs.getString("artist"));
                    record.setAlbum(rs.getString("album"));
                    record.setTrackNumber(rs.getString("track_number"));
                    record.setFingerprint(rs.getString("fingerprint"));
                    record.setFilePath(rs.getString("file_path"));
                    results.add(record);
                }
            }
        } catch (SQLException ignored) {
        }
        return results;
    }
}
