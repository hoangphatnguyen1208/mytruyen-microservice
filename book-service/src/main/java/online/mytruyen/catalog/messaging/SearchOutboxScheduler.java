package online.mytruyen.catalog.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "catalog.search-sync.enabled", havingValue = "true")
public class SearchOutboxScheduler {
    private static final Logger log = LoggerFactory.getLogger(SearchOutboxScheduler.class);
    private final SearchOutboxPublisher publisher;

    public SearchOutboxScheduler(SearchOutboxPublisher publisher) { this.publisher = publisher; }

    @Scheduled(fixedDelayString = "${catalog.search-sync.poll-delay-ms:1000}")
    public void poll() {
        try {
            int count = publisher.publishBatch();
            if (count > 0) log.info("search_outbox published={}", count);
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("search_outbox deferred reason={}", exception.getClass().getSimpleName());
        }
    }
}
