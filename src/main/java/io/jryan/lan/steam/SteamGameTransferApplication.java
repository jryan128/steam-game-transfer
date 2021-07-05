package io.jryan.lan.steam;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import me.marnic.jiconextract2.JIconExtract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class SteamGameTransferApplication extends Application {

    private static final Logger logger = LoggerFactory.getLogger(SteamGameTransferApplication.class);
    private final Image questionMarkImage = new Image("/icons8-question-mark-48.png");
    private final Downloader downloader = new Downloader();

    public SteamGameTransferApplication() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            downloader.close();
            try {
                PowerManagement.INSTANCE.allowSleep();
            } catch (Exception e) {
                logger.warn("Could not allow sleep", e);
            }
        }, "downloader and powercfg cleanup thread"));
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            setupStage(primaryStage);
        } catch (Exception e) { // JavaFX doesn't report stack trace if start() throws Exception so wrap everything
            logger.error("Could not setup primary stage", e);
            throw e;
        }
    }

    private void setupStage(Stage primaryStage) {
        logger.info("Starting application");
        logger.info("Setting up primary stage");
        primaryStage.getIcons().addAll(new Image("/icon.png"));
        primaryStage.setTitle("Steam Game Transfer");
        primaryStage.setMaxWidth(1000);
        primaryStage.setMaxHeight(1000);
        primaryStage.setMinWidth(700);
        primaryStage.setMinHeight(700);
        primaryStage.setResizable(true);

        logger.info("Setting up main dialog");
        var dialogUrl = Objects.requireNonNull(getClass().getResource("/dialog.fxml"), "Could not find /dialog.fxml");
        var fxmlLoader = new FXMLLoader(dialogUrl);
        fxmlLoader.setController(new Controller(downloader));
        Parent parent;
        try {
            parent = fxmlLoader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load /dialog.fxml", e);
        }

        logger.debug("Setting up #gameList");
        @SuppressWarnings("unchecked") ListView<Game> gameList = (ListView<Game>) parent.lookup("#gameList");
        gameList.setCellFactory(p -> new GameListCell());
        gameList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        logger.debug("Setting up #remoteFolderTextField");
        TextField remoteFolderTextField = (TextField) parent.lookup("#remoteFolderTextField");
        remoteFolderTextField.textProperty().addListener((o, oldValue, newValue) -> {
            if (oldValue.equals(newValue)) {
                return;
            }
            gameList.setDisable(true);
            var originalTitle = primaryStage.getTitle();
            primaryStage.setTitle(originalTitle + " - Loading Game List, please wait...");
            CompletableFuture.runAsync(() -> {
                var root = Paths.get(remoteFolderTextField.getText());
                try (var paths = Files.walk(root, 1)) {
                    var games = paths
                            .filter(Files::isDirectory)
                            .filter(p -> !p.equals(root))
                            .map(p -> new Game(p, extractIconFromAnExeInGamePath(p)))
                            .collect(Collectors.toList());
                    Platform.runLater(() -> {
                        gameList.setItems(FXCollections.observableArrayList(games));
                        gameList.setDisable(false);
                        primaryStage.setTitle(originalTitle);
                    });
                } catch (IOException e) {
                    logger.error("Could not load game list", e);
                    Platform.runLater(() -> {
                        gameList.setItems(FXCollections.observableArrayList());
                        gameList.setDisable(true);
                        primaryStage.setTitle(originalTitle);
                        new Alert(Alert.AlertType.ERROR, "Could not load game list, try another location", ButtonType.CLOSE).showAndWait();
                    });
                }
            });
        });
        remoteFolderTextField.textProperty().set("Z:\\Steam");

        var scene = new Scene(parent);
        primaryStage.setScene(scene);
        logger.info("Showing stage");
        primaryStage.show();
    }

    private ImageView extractIconFromAnExeInGamePath(Path gamePath) {
        try (var files = Files.walk(gamePath, 1)) {
            var exes = files
                    .filter(Files::isRegularFile)
                    .filter(this::endsInExe)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString(), new UnityErrorExeComparator()))
                    .collect(Collectors.toList());
            for (Path exe : exes) {
                try {
                    int iconSize = 48;
                    var iconForFile = JIconExtract.getIconForFile(iconSize, iconSize, exe.toFile());
                    var img = new WritableImage(iconSize, iconSize);
                    return new ImageView(SwingFXUtils.toFXImage(iconForFile, img));
                } catch (Exception e) {
                    logger.error("Error loading icon for game {}, trying next exe", gamePath);
                }
            }
        } catch (IOException e) {
            logger.warn("Error walking gamePath {}", gamePath, e);
        }
        logger.warn("Could not extract icon for {} from any exe, moving on", gamePath);
        return new ImageView(questionMarkImage);
    }

    private boolean endsInExe(Path p) {
        return p.getFileName().toString().toLowerCase().endsWith(".exe");
    }

    @Override
    public void stop() {
        System.exit(0);
    }
}
