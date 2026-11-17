package feedlab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import feedlab.service.domain.RunStatus;
import feedlab.service.domain.SimulationRun;
import feedlab.service.domain.SimulationRunRepository;
import feedlab.service.run.RunLauncher;
import feedlab.service.web.RunRequest;
import feedlab.sim.SyntheticItchWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Drives the real application over HTTP against a real Postgres: Flyway creates the schema,
 * Hibernate validates it, a run is queued, executed in the background and read back.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RunApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static Path dataDir;
    private static long messagesInFile;

    @BeforeAll
    static void writeInput() throws IOException {
        dataDir = Files.createTempDirectory("feedlab-test-data");
        messagesInFile = SyntheticItchWriter.write(dataDir.resolve("synthetic.itch"), 6, 5_000, 3);
        Files.writeString(dataDir.resolve("not-itch.txt"), "this is not market data");
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("feedlab.data-dir", () -> dataDir.toString());
    }

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private SimulationRunRepository runs;

    @Autowired
    private RunLauncher launcher;

    private JsonNode awaitFinished(String location) {
        return await().atMost(Duration.ofSeconds(30)).until(
                () -> http.getForObject(location, JsonNode.class),
                run -> List.of("COMPLETED", "FAILED").contains(run.get("status").asText()));
    }

    @Test
    void aRunIsQueuedExecutedStoredAndReadBack() {
        Map<String, Object> request = Map.of(
                "inputFile", "synthetic.itch",
                "lineA", Map.of("fades", List.of(Map.of("startSeconds", 2, "lengthSeconds", 2, "loss", 0.3))));

        ResponseEntity<JsonNode> created = http.postForEntity("/api/runs", request, JsonNode.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(created.getBody().get("status").asText()).isEqualTo("QUEUED");
        String location = created.getHeaders().getLocation().toString();
        assertThat(location).endsWith("/api/runs/" + created.getBody().get("id").asText());

        JsonNode run = awaitFinished(location);
        assertThat(run.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(run.get("error").isNull()).isTrue();

        // The configuration is echoed back with defaults filled in.
        assertThat(run.at("/config/lineA/latencyMicros").asDouble()).isEqualTo(3_980.0);
        assertThat(run.at("/config/lineA/fades/0/lengthSeconds").asDouble()).isEqualTo(2.0);
        assertThat(run.at("/config/seed").asLong()).isEqualTo(42);

        JsonNode results = run.get("results");
        assertThat(results.get("messages").asLong()).isEqualTo(messagesInFile);
        assertThat(results.get("messagesLost").asLong()).isZero();
        assertThat(results.get("latency")).hasSize(6); // CLEAR and FADE, each for LINE_A, LINE_B, ARBITRATED

        JsonNode fadeLineA = find(results.get("latency"), "FADE", "LINE_A");
        JsonNode fadeArbitrated = find(results.get("latency"), "FADE", "ARBITRATED");
        assertThat(fadeLineA.get("messagesLost").asLong()).isPositive();
        assertThat(fadeArbitrated.get("messagesLost").asLong()).isZero();
        assertThat(fadeArbitrated.get("messagesWon").isNull()).isTrue();
        assertThat(fadeArbitrated.get("p50Micros").asDouble()).isBetween(3_900.0, 6_600.0);

        JsonNode timeline = http.getForObject(location + "/timeline", JsonNode.class);
        long delivered = 0;
        for (JsonNode second : timeline) {
            delivered += second.get("delivered").asLong();
        }
        assertThat(delivered).isEqualTo(messagesInFile);

        JsonNode list = http.getForObject("/api/runs", JsonNode.class);
        assertThat(list.findValuesAsText("id")).contains(run.get("id").asText());
    }

    private static JsonNode find(JsonNode latency, String segment, String source) {
        for (JsonNode row : latency) {
            if (row.get("segment").asText().equals(segment) && row.get("source").asText().equals(source)) {
                return row;
            }
        }
        throw new AssertionError("no latency row for " + segment + " " + source);
    }

    @Test
    void twoRunsWithTheSameSeedStoreIdenticalResults() {
        Map<String, Object> request = Map.of("inputFile", "synthetic.itch", "lineA", Map.of("loss", 0.05), "seed", 9);

        JsonNode first = awaitFinished(http.postForEntity("/api/runs", request, JsonNode.class)
                .getHeaders().getLocation().toString());
        JsonNode second = awaitFinished(http.postForEntity("/api/runs", request, JsonNode.class)
                .getHeaders().getLocation().toString());

        assertThat(second.get("results")).isEqualTo(first.get("results"));
    }

    @Test
    void aFileThatIsNotMarketDataFailsTheRunWithAReason() {
        ResponseEntity<JsonNode> created =
                http.postForEntity("/api/runs", Map.of("inputFile", "not-itch.txt"), JsonNode.class);

        JsonNode run = awaitFinished(created.getHeaders().getLocation().toString());

        assertThat(run.get("status").asText()).isEqualTo("FAILED");
        assertThat(run.get("error").asText()).isNotBlank();
        assertThat(run.get("results").isNull()).isTrue();
    }

    @Test
    void rejectsAnUnknownInputFile() {
        ResponseEntity<JsonNode> response =
                http.postForEntity("/api/runs", Map.of("inputFile", "missing.itch"), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("detail").asText()).contains("missing.itch");
    }

    @Test
    void rejectsAnInputFileOutsideTheDataDirectory() {
        ResponseEntity<JsonNode> response =
                http.postForEntity("/api/runs", Map.of("inputFile", "../../etc/passwd"), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsOutOfRangeFieldsAndBadTimes() {
        assertThat(http.postForEntity("/api/runs", Map.of("lineA", Map.of("loss", 0.5)), JsonNode.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST); // no inputFile
        assertThat(http.postForEntity(
                                "/api/runs",
                                Map.of("inputFile", "synthetic.itch", "lineA", Map.of("loss", 1.5)),
                                JsonNode.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(http.postForEntity(
                                "/api/runs", Map.of("inputFile", "synthetic.itch", "start", "half past nine"), JsonNode.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(http.postForEntity(
                                "/api/runs",
                                Map.of("inputFile", "synthetic.itch", "start", "10:00:00", "end", "09:00:00"),
                                JsonNode.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void anUnknownRunIsNotFound() {
        String id = UUID.randomUUID().toString();

        assertThat(http.getForEntity("/api/runs/" + id, JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(http.getForEntity("/api/runs/" + id + "/timeline", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listsTheAvailableInputFiles() {
        JsonNode inputs = http.getForObject("/api/inputs", JsonNode.class);

        assertThat(inputs.findValuesAsText("")).isEmpty();
        assertThat(inputs).extracting(JsonNode::asText).contains("synthetic.itch", "not-itch.txt");
    }

    @Test
    void aRunLeftUnfinishedByARestartIsMarkedFailedOnStartup() {
        RunRequest request = new RunRequest("synthetic.itch", null, null, null, null, null, null, null);
        SimulationRun orphan = runs.save(new SimulationRun(UUID.randomUUID(), Instant.now(), request.toConfig(), List.of()));

        launcher.run(new DefaultApplicationArguments());

        SimulationRun after = runs.findById(orphan.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(RunStatus.FAILED);
        assertThat(after.getErrorMessage()).contains("restart");
    }
}
