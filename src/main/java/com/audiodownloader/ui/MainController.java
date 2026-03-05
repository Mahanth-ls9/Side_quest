package com.audiodownloader.ui;

import com.audiodownloader.config.AppProperties;
import com.audiodownloader.library.DuplicateDetector;
import com.audiodownloader.library.LibraryIndexService;
import com.audiodownloader.metadata.TrackMetadata;
import com.audiodownloader.queue.QueueEventListener;
import com.audiodownloader.queue.QueueManager;
import com.audiodownloader.service.DownloadHistoryService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import com.audiodownloader.service.DownloadService;
import com.audiodownloader.ui.model.DownloadStatus;
import com.audiodownloader.ui.model.QueueItem;
import com.audiodownloader.ui.model.TrackInfo;
import jakarta.annotation.PreDestroy;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.util.Duration;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.UUID;

@Component
public class MainController implements Initializable, QueueEventListener {
    @FXML
    private TextField urlField;
    @FXML
    private TextField downloadFolderField;
    @FXML
    private TableView<TrackInfo> playlistTable;
    @FXML
    private TableColumn<TrackInfo, Boolean> selectColumn;
    @FXML
    private TableColumn<TrackInfo, String> titleColumn;
    @FXML
    private TableColumn<TrackInfo, String> durationColumn;
    @FXML
    private TableColumn<TrackInfo, String> channelColumn;
    @FXML
    private TableColumn<TrackInfo, String> playlistStatusColumn;
    @FXML
    private TableView<QueueItem> queueTable;
    @FXML
    private TableColumn<QueueItem, String> queueStatusColumn;
    @FXML
    private TableColumn<QueueItem, String> queueTitleColumn;
    @FXML
    private TableColumn<QueueItem, String> queueProgressColumn;
    @FXML
    private TableColumn<QueueItem, String> queueSpeedColumn;
    @FXML
    private TextArea logsArea;
    @FXML
    private ListView<String> completedList;

    private final DownloadService downloadService;
    private final QueueManager queueManager;
    private final LibraryIndexService libraryIndexService;
    private final DuplicateDetector duplicateDetector;
    private final AppProperties appProperties;
    private final DownloadHistoryService downloadHistoryService;

    private final ObservableList<TrackInfo> playlistItems = FXCollections.observableArrayList();
    private final ObservableList<QueueItem> queueItems = FXCollections.observableArrayList();
    private final ObservableList<String> completedItems = FXCollections.observableArrayList();
    private Timeline clipboardWatchdog;

    public MainController(DownloadService downloadService,
                          QueueManager queueManager,
                          LibraryIndexService libraryIndexService,
                          DuplicateDetector duplicateDetector,
                          AppProperties appProperties,
                          DownloadHistoryService downloadHistoryService) {
        this.downloadService = downloadService;
        this.queueManager = queueManager;
        this.libraryIndexService = libraryIndexService;
        this.duplicateDetector = duplicateDetector;
        this.appProperties = appProperties;
        this.downloadHistoryService = downloadHistoryService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        queueManager.addListener(this);
        libraryIndexService.startupScan();
        downloadFolderField.setText(appProperties.getMusicFolder());
        setupPlaylistTable();
        setupQueueTable();
        completedList.setItems(completedItems);
        completedItems.addAll(downloadHistoryService.loadRecent(100));
        enableDragDropUrl();
        enableClipboardDetection();
    }

    @FXML
    public void onFetchPlaylist() {
        String url = urlField.getText();
        if (url == null || url.isBlank()) {
            appendLog("Please enter a valid URL.");
            return;
        }
        appendLog("Fetching playlist: " + url);
        List<TrackInfo> tracks = downloadService.fetchPlaylist(url);
        for (TrackInfo track : tracks) {
            track.setId(track.getId() == null ? UUID.randomUUID().toString() : track.getId());
            TrackMetadata metadata = new TrackMetadata();
            metadata.setTitle(track.getTitle());
            metadata.setArtist(track.getChannel());
            metadata.setAlbum("Unknown Album");
            if (duplicateDetector.isDuplicate(track.getTitle() + ".m4a", metadata)) {
                track.setStatus(DownloadStatus.DUPLICATE);
                track.setSelected(false);
            } else {
                track.setStatus(DownloadStatus.READY);
            }
        }
        playlistItems.setAll(tracks);
    }

    @FXML
    public void onDownloadSelected() {
        for (TrackInfo track : playlistItems) {
            if (track.isSelected() && track.getStatus() != DownloadStatus.DUPLICATE) {
                queueManager.enqueue(track);
                track.setStatus(DownloadStatus.WAITING);
            }
        }
        playlistTable.refresh();
    }

    @FXML
    public void onPause() {
        queueManager.pauseAll();
    }

    @FXML
    public void onResume() {
        queueManager.resumeAll();
    }

    @FXML
    public void onCancelSelected() {
        QueueItem selected = queueTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            queueManager.cancel(selected.getTrackInfo().getId());
        }
    }

    @FXML
    public void onClearFinished() {
        queueManager.clearFinished();
        queueItems.setAll(queueManager.getQueueItems());
    }

    private void setupPlaylistTable() {
        selectColumn.setCellValueFactory(cellData -> {
            TrackInfo item = cellData.getValue();
            SimpleBooleanProperty property = new SimpleBooleanProperty(item.isSelected());
            property.addListener((obs, oldVal, newVal) -> item.setSelected(newVal));
            return property;
        });
        selectColumn.setCellFactory(CheckBoxTableCell.forTableColumn(selectColumn));
        titleColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getTitle()));
        durationColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getDuration()));
        channelColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getChannel()));
        playlistStatusColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getStatus().name()));
        playlistTable.setItems(playlistItems);
    }

    private void setupQueueTable() {
        queueStatusColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getStatus().name()));
        queueTitleColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getTrackInfo().getTitle()));
        queueProgressColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty((int) (cellData.getValue().getProgress() * 100) + "% (ETA " + cellData.getValue().getEta() + ")"));
        queueProgressColumn.setCellFactory(column -> new TableCell<>() {
            private final ProgressBar progressBar = new ProgressBar(0);

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= queueTable.getItems().size()) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                QueueItem queueItem = queueTable.getItems().get(getIndex());
                progressBar.setProgress(queueItem.getProgress());
                setGraphic(progressBar);
                setText(item);
            }
        });
        queueSpeedColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getSpeed()));
        queueTable.setItems(queueItems);
    }

    @Override
    public void onQueueItemUpdated(QueueItem item) {
        Platform.runLater(() -> {
            queueItems.setAll(queueManager.getQueueItems());
            queueTable.refresh();
        });
    }

    @Override
    public void onQueueItemCompleted(QueueItem item) {
        Platform.runLater(() -> {
            String line = item.getTrackInfo().getTitle() + " - " + item.getStatus();
            completedItems.add(line);
            downloadHistoryService.append(item);
            queueItems.setAll(queueManager.getQueueItems());
        });
    }

    @Override
    public void onLog(String message) {
        appendLog(message);
    }

    private void appendLog(String message) {
        Platform.runLater(() -> logsArea.appendText(message + System.lineSeparator()));
    }

    private void enableDragDropUrl() {
        urlField.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasString()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        urlField.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasString()) {
                urlField.setText(db.getString().trim());
                event.setDropCompleted(true);
            } else {
                event.setDropCompleted(false);
            }
            event.consume();
        });
    }

    private void enableClipboardDetection() {
        clipboardWatchdog = new Timeline(new KeyFrame(Duration.seconds(3), event -> {
            String current = urlField.getText();
            if (current != null && !current.isBlank()) {
                return;
            }
            String clip = Clipboard.getSystemClipboard().getString();
            if (clip != null && (clip.contains("youtube.com/watch") || clip.contains("youtu.be/"))) {
                urlField.setText(clip.trim());
            }
        }));
        clipboardWatchdog.setCycleCount(Timeline.INDEFINITE);
        clipboardWatchdog.play();
    }

    @PreDestroy
    public void shutdown() {
        if (clipboardWatchdog != null) {
            clipboardWatchdog.stop();
        }
    }
}
