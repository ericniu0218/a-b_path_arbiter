package feedlab.service;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param dataDir the only directory input files may be read from */
@ConfigurationProperties("feedlab")
public record FeedlabProperties(Path dataDir) {}
