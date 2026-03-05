package com.audiodownloader.ui;

import com.audiodownloader.config.AppProperties;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "com.audiodownloader")
@EnableConfigurationProperties(AppProperties.class)
public class AudioDownloaderApplication extends Application {
    private ConfigurableApplicationContext context;

    @Override
    public void init() {
        context = new SpringApplicationBuilder(AudioDownloaderApplication.class)
                .web(WebApplicationType.NONE)
                .run();
    }

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/layout.fxml"));
        loader.setControllerFactory(context::getBean);
        Parent root = loader.load();

        Scene scene = new Scene(root, 1320, 820);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        stage.setTitle("Audio Downloader Pro");
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            try {
                if (context != null) {
                    context.close();
                }
            } finally {
                Platform.exit();
                System.exit(0);
            }
        });
        stage.show();
    }

    @Override
    public void stop() {
        if (context != null) {
            context.close();
        }
        System.exit(0);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
