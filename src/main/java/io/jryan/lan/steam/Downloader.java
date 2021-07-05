package io.jryan.lan.steam;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.stage.Modality;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Downloader implements AutoCloseable {

    private static final double BYTES_IN_GIB = 1_073_741_824.0;
    private static final Logger logger = LoggerFactory.getLogger(Downloader.class);
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    /**
     * Download a game into Path {@code to}. Create dialog to keep track of the download.
     *
     * @param game   game to download
     * @param toPath target path to download game to
     * @throws UncheckedIOException if folder for game could not be created in target path {@code to}
     */
    @FXML
    public Dialog<Void> downloadGames(Game game, Path toPath) {
        DialogPane dialogPane = loadDialogPaneFromFXML();

        logger.info("Downloading {}", game.name());
        var from = game.path();
        var to = toPath.resolve(game.name());

        logger.debug("Create folder for game in target {} if it doesn't exist", to);
        if (Files.notExists(to)) {
            try {
                Files.createDirectory(to);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not create folder " + to, e);
            }
        }

        logger.debug("Setting up nodes in dialog pane");
        var gameNameLabel = (Label) dialogPane.lookup("#gameNameLabel");
        gameNameLabel.setText("Downloading " + game.name() + "...");

        var progressBar = (ProgressBar) dialogPane.lookup("#progressBar");
        var percentLabel = (Label) dialogPane.lookup("#percentLabel");

        if (game.icon() != null) {
            var image = (ImageView) dialogPane.lookup("#image");
            image.setImage(game.icon().getImage());
        }

        var visitor = new CopyFileVisitor(to);

        var fromSizeFuture = CompletableFuture.supplyAsync(() -> size(from));
        Instant start = Instant.now();
        AtomicBoolean done = new AtomicBoolean(false);
        AtomicBoolean started = new AtomicBoolean(false);

        logger.debug("Schedule thread that updates dialog box");
        var progressTrackerScheduledFuture = scheduledExecutorService.scheduleAtFixedRate(
                () -> {
                    Long fromSize = null;
                    try {
                        if (fromSizeFuture.isDone()) {
                            fromSize = fromSizeFuture.get();
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        logger.error("Could not calculate folder size", e);
                    }

                    final String percentString;
                    final double percent;
                    if (!started.get()) {
                        percent = 0.0d;
                        percentString = "Paused, waiting for others to complete";
                    } else if (fromSize != null) {
                        var size = visitor.getBytesTransferred();
                        percent = (float) size / fromSize;
                        double gbs = size / BYTES_IN_GIB;
                        percentString = "%.1f%% %.2f/%.2f GB".formatted(percent * 100, gbs, fromSize / BYTES_IN_GIB);
                    } else {
                        percentString = "Calculating size of game...";
                        percent = ProgressBar.INDETERMINATE_PROGRESS;
                    }

                    Platform.runLater(() -> {
                        if (!done.get()) {
                            progressBar.setProgress(percent);
                            percentLabel.textProperty().set(percentString);
                        }
                    });
                },
                1, 1, TimeUnit.SECONDS
        );

        var dialog = new Dialog<Void>();
        dialog.initModality(Modality.NONE);
        var cancelButton = (Button) dialogPane.lookupButton(ButtonType.CANCEL);

        logger.debug("Execute code that downloads game in background thread");
        var downloadFuture = executorService.submit(() -> {
            try {
                logger.debug("Starting download for game {}, from {} to {}", game, from, to);
                started.set(true);
                try {
                    Files.walkFileTree(from, visitor);
                } catch (IOException e) {
                    logger.error("Could not copy files", e);
                    throw new UncheckedIOException(e);
                }
                done.set(true);

                if (Thread.interrupted()) {
                    logger.info("Download task for {} canceled", game.name());
                    throw new RuntimeException("Canceled");
                }

                logger.debug("Cancel process tracking since download is finished");
                progressTrackerScheduledFuture.cancel(true);

                var downloadDuration = Duration.between(start, Instant.now());
                long s = downloadDuration.getSeconds();
                var formattedDuration = "%d:%02d:%02d".formatted(s / 3600, (s % 3600) / 60, (s % 60));

                @Nullable Long fromSize = null;
                try {
                    if (fromSizeFuture.isDone()) {
                        fromSize = fromSizeFuture.get();
                    }
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Could not calculate remote size for game {}", game.name(), e);
                }

                final String percentString;
                if (fromSize != null) {
                    percentString = "%.1f%% %.2f/%.2f GB; finished in %s".formatted(100.0, fromSize / BYTES_IN_GIB, fromSize / BYTES_IN_GIB, formattedDuration);
                } else {
                    var size = visitor.getBytesTransferred();
                    double gbs = size / BYTES_IN_GIB;
                    percentString = "100%% %.2f GB; finished in %s".formatted(gbs, formattedDuration);
                }

                Platform.runLater(() -> {
                    progressBar.setProgress(100.0);
                    gameNameLabel.textProperty().set("Downloaded " + game.name());
                    dialogPane.getButtonTypes().setAll(ButtonType.OK);
                    percentLabel.textProperty().set(percentString);
                });
            } finally {
                PowerManagement.INSTANCE.allowSleep();
                progressTrackerScheduledFuture.cancel(true);
                fromSizeFuture.cancel(true);
            }
        });

        cancelButton.setOnAction(a -> {
            downloadFuture.cancel(true);
            progressTrackerScheduledFuture.cancel(true);
        });

        dialog.setDialogPane(dialogPane);
        dialog.show();
        return dialog;
    }

    private static DialogPane loadDialogPaneFromFXML() {
        logger.debug("Loading dialog pane from FXML");
        var dialogUrl = Objects.requireNonNull(Downloader.class.getResource("/download-dialog.fxml"), "Cannot find /download-dialog.fxml on classpath");
        try {
            return FXMLLoader.load(dialogUrl);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load dialog from FXML /download-dialog.fxml", e);
        }
    }

    /**
     * Calculate the size of {@code path}
     *
     * @param path to calculate the size of
     * @return size of all files in path recursively down in bytes
     */
    private static long size(Path path) {
        try {
            return Files.walk(path)
                    .filter(p -> p.toFile().isFile())
                    .mapToLong(p -> p.toFile().length())
                    .sum();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not calculate size of path " + path, e);
        }
    }

    @Override
    public void close() {
        executorService.shutdownNow();
        scheduledExecutorService.shutdownNow();
    }
}
