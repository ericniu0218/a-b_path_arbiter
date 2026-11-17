package feedlab.service.run;

import feedlab.service.FeedlabProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** Finds input files, and refuses anything outside the configured data directory. */
@Component
public class InputFiles {

    private final Path dataDir;

    public InputFiles(FeedlabProperties properties) {
        this.dataDir = properties.dataDir().toAbsolutePath().normalize();
    }

    /** @throws InvalidRunRequestException if the name escapes the data directory or is not a file */
    public Path resolve(String name) {
        Path file = dataDir.resolve(name).normalize();
        if (!file.startsWith(dataDir) || file.equals(dataDir)) {
            throw new InvalidRunRequestException("inputFile must name a file inside the data directory");
        }
        if (!Files.isRegularFile(file)) {
            throw new InvalidRunRequestException("no such input file: " + name);
        }
        return file;
    }

    public List<String> list() {
        if (!Files.isDirectory(dataDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dataDir)) {
            return files.filter(Files::isRegularFile)
                    .map(f -> f.getFileName().toString())
                    .filter(n -> !n.startsWith("."))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
