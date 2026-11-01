package campusrider.config;

import campusrider.storage.CsvStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.io.IOException;
import java.nio.file.Path;

/** Creates the single CsvStorage used by every repository and locks the data folder. */
@Configuration
public class StorageConfig {
    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    private CsvStorage storage;

    @Bean(destroyMethod = "close")
    public CsvStorage csvStorage(AppProperties properties) throws IOException {
        storage = new CsvStorage(Path.of(properties.dataDir()));
        storage.lockDataDirectory();
        log.info("Campus Rider CSV data folder: {}", storage.getDataDir());
        return storage;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reportWarnings() {
        if (storage != null) {
            for (String warning : storage.getWarnings()) {
                log.warn("[data] {}", warning);
            }
        }
    }
}
