package online.mytruyen.catalog.messaging;

import online.mytruyen.catalog.repository.SearchOutboxRepository;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "catalog.search-sync.enabled", havingValue = "true")
public class SearchOutboxPublisher {
    private final SearchOutboxRepository events;
    private final RabbitTemplate rabbit;

    public SearchOutboxPublisher(SearchOutboxRepository events, RabbitTemplate rabbit) {
        this.events = events;
        this.rabbit = rabbit;
    }

    @Transactional(rollbackFor = Exception.class)
    public int publishBatch() throws Exception {
        var batch = events.lockPending(PageRequest.of(0, 10));
        for (var event : batch) {
            String payload = "{\"schema_version\":1,\"book_id\":" + event.getBookId() + "}";
            var message = MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(event.getEventId().toString()).build();
            var correlation = new CorrelationData(event.getEventId().toString());
            rabbit.send(SearchMessagingConfig.EXCHANGE, "book.changed", message, correlation);
            var confirmation = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirmation.ack() || correlation.getReturned() != null)
                throw new IllegalStateException("Search event was not routed and confirmed");
            event.setPublishedAt(Instant.now());
        }
        // A crash before commit can duplicate delivery; the consumer rehydrates current state.
        return batch.size();
    }
}
