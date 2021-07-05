package io.jryan.lan.steam;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Paths;

/**
 * The methods the FXML references when actions are taken by the user.
 */
public class Controller {
    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    @FXML
    private Window window;

    @FXML
    private TextField localFolderTextField;

    @FXML
    private TextField remoteFolderTextField;

    @FXML
    private ListView<Game> gameList;

    private final Downloader downloader;

    public Controller(Downloader downloader) {
        this.downloader = downloader;
    }

    /**
     * Browse button for local folder
     */
    @FXML
    public void onBrowseLocalFolder() {
        askUserToChooseFolder("Open Local Steam Folder", localFolderTextField);
    }

    /**
     * Browse button for remote folder
     */
    @FXML
    public void onBrowseRemoteFolder() {
        askUserToChooseFolder("Open Remote Steam Folder", remoteFolderTextField);
    }

    public void askUserToChooseFolder(String title, TextField textField) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(title);
        var initialDirectory = new File(textField.getText());
        if (initialDirectory.exists()) {
            directoryChooser.setInitialDirectory(initialDirectory);
        }
        var file = directoryChooser.showDialog(window);
        if (file != null) {
            textField.setText(file.getAbsolutePath());
        }
    }

    /**
     * Download button
     */
    @FXML
    public void downloadGames() {
        try {
            try {
                PowerManagement.INSTANCE.preventSleep();
            } catch (Exception e) {
                logger.error("Could not prevent sleep, continuing anyway");
            }
            Double lastYPosition = null;
            for (Game game : gameList.getSelectionModel().getSelectedItems()) {
                var dialog = downloader.downloadGames(game, Paths.get(localFolderTextField.getText()));
                if (lastYPosition != null) {
                    dialog.setY(lastYPosition + dialog.getHeight());
                }
                lastYPosition = dialog.getY();
            }
        } catch (Exception e) {
            PowerManagement.INSTANCE.allowSleep();
            logger.error("Could not allow sleep, continuing anyway");
            Alert alert = new Alert(Alert.AlertType.ERROR, "Could not download games\n" + e.getMessage());
            alert.show();
        }
    }

    /**
     * Exit button
     */
    @FXML
    public void exit() {
        Platform.exit();
        System.exit(0);
    }
}
