package io.jryan.lan.steam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Visits file in a {@code Path} and copies them to {@code targetPath} set by the constructor.
 * Example usage:
 * <p>
 * {@code
 * Files.walkFileTree(from, new CopyFileVisitor(target));
 * }
 */
@ThreadSafe
public class CopyFileVisitor extends SimpleFileVisitor<Path> {
    private static final Logger logger = LoggerFactory.getLogger(CopyFileVisitor.class);
    private final Path targetPath;
    private final AtomicLong bytesTransferred = new AtomicLong();
    private Path sourcePath = null;

    public CopyFileVisitor(Path targetPath) {
        this.targetPath = targetPath;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            return FileVisitResult.TERMINATE;
        }
        if (sourcePath == null) {
            sourcePath = dir;
        } else {
            var path = targetPath.resolve(sourcePath.relativize(dir));
            logger.debug("Creating folder {}", path);
            if (Files.notExists(path)) {
                Files.createDirectories(path);
            }
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            return FileVisitResult.TERMINATE;
        }
        var target = targetPath.resolve(sourcePath.relativize(file));
        if (Files.notExists(target)) {
            logger.debug("Copying {}", file);
            Files.copy(file, target);
            logger.debug("Done Copying {}", file);
        } else {
            logger.debug("Already exists {}", file);
        }
        var bytes = file.toFile().length();
        logger.debug("Bytes transferred for {}: {}", target, bytes);
        bytesTransferred.addAndGet(bytes);
        return FileVisitResult.CONTINUE;
    }

    /**
     * Get the current amount of bytes transferred by this visitor.
     * @return bytes transferred so far
     */
    public long getBytesTransferred() {
        return bytesTransferred.get();
    }

    /**
     * Convenience function to copy files from a Path to another.
     * @throws UncheckedIOException if files could not be copied
     */
    private static void copy(Path from, Path to) {
        try {
            Files.walkFileTree(from, new CopyFileVisitor(to));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not copy files", e);
        }
    }
}
