package io.jryan.lan.steam;

import javafx.scene.image.ImageView;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public record Game(Path path, @Nullable ImageView icon) {

    public String name() {
        return path.getFileName().toString();
    }
}
