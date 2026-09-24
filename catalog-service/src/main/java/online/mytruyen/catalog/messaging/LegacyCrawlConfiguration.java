package online.mytruyen.catalog.messaging;

import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration
@ConditionalOnProperty(name="catalog.legacy-worker.enabled",havingValue="true")
public class LegacyCrawlConfiguration {
    @Bean Queue legacyCrawlQueue(@Value("${catalog.legacy-worker.queue}") String name) {
        if (name.isBlank() || name.startsWith("amq.") || name.length()>200)
            throw new IllegalArgumentException("A valid crawl queue name is required");
        // Same declaration as the original Go worker: durable, non-exclusive, no arguments.
        return new Queue(name,true,false,false);
    }
}
