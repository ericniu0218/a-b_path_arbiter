package feedlab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import feedlab.service.run.InputFiles;
import feedlab.service.run.InvalidRunRequestException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InputFilesTest {

    @TempDir
    Path root;

    private Path dataDir;
    private InputFiles inputFiles;

    @BeforeEach
    void setUp() throws IOException {
        dataDir = Files.createDirectory(root.resolve("data"));
        Files.writeString(dataDir.resolve("day.itch"), "x");
        Files.writeString(root.resolve("secret.txt"), "x");
        inputFiles = new InputFiles(new FeedlabProperties(dataDir));
    }

    @Test
    void resolvesAFileInsideTheDataDirectory() {
        assertThat(inputFiles.resolve("day.itch")).isEqualTo(dataDir.resolve("day.itch").toAbsolutePath());
    }

    @Test
    void rejectsAPathThatClimbsOutOfTheDataDirectory() {
        assertThatThrownBy(() -> inputFiles.resolve("../secret.txt")).isInstanceOf(InvalidRunRequestException.class);
    }

    @Test
    void rejectsAnAbsolutePath() {
        String absolute = root.resolve("secret.txt").toAbsolutePath().toString();

        assertThatThrownBy(() -> inputFiles.resolve(absolute)).isInstanceOf(InvalidRunRequestException.class);
    }

    @Test
    void rejectsAFileThatDoesNotExistAndTheDirectoryItself() {
        assertThatThrownBy(() -> inputFiles.resolve("missing.itch")).isInstanceOf(InvalidRunRequestException.class);
        assertThatThrownBy(() -> inputFiles.resolve(".")).isInstanceOf(InvalidRunRequestException.class);
    }

    @Test
    void listsOnlyRegularVisibleFiles() throws IOException {
        Files.writeString(dataDir.resolve(".hidden"), "x");
        Files.createDirectory(dataDir.resolve("sub"));

        assertThat(inputFiles.list()).containsExactly("day.itch");
    }
}
