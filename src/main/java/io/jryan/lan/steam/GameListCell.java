package io.jryan.lan.steam;

import javafx.scene.control.ListCell;

/**
 * Takes a {@link Game} and renders it as a row in a List View.
 */
public class GameListCell extends ListCell<Game> {
    @Override
    public void updateItem(Game exe, boolean empty) {
        super.updateItem(exe, empty);
        if (empty) {
            setText(null);
            setGraphic(null);
        } else {
            setText(exe.name());
            setGraphic(exe.icon());
        }
    }
}
