package online.mytruyen.catalog.messaging;

import online.mytruyen.catalog.exception.ApiException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class LegacyCrawlPublisher {
    public enum Command {
        GENRES("crawl_genres"), TAGS("crawl_tags"), STATUSES("crawl_book_statuses"),
        ALL_BOOKS("crawl_all_books"), BOOK("crawl_book"), CHAPTERS("crawl_chapters");
        final String type;
        Command(String type) { this.type=type; }
    }
    private final RabbitTemplate rabbit;
    private final boolean enabled;
    private final String queue;
    private final long confirmTimeoutMs;
    public LegacyCrawlPublisher(RabbitTemplate rabbit,
            @Value("${catalog.legacy-worker.enabled:false}") boolean enabled,
            @Value("${catalog.legacy-worker.queue:}") String queue,
            @Value("${catalog.legacy-worker.confirm-timeout-ms:5000}") long confirmTimeoutMs) {
        this.rabbit=rabbit; this.enabled=enabled; this.queue=queue; this.confirmTimeoutMs=confirmTimeoutMs;
        if (enabled && (queue.isBlank() || queue.startsWith("amq.") || queue.length()>200))
            throw new IllegalArgumentException("A valid crawl queue name is required");
        if (confirmTimeoutMs<1 || confirmTimeoutMs>30000) throw new IllegalArgumentException("Invalid confirm timeout");
    }
    public void publish(Command command,Long bookId) {
        if (!enabled) throw new ApiException(503,"Legacy worker compatibility is disabled");
        boolean needsBook=command==Command.BOOK || command==Command.CHAPTERS;
        if (needsBook && (bookId==null || bookId<=0)) throw new ApiException(400,"Positive source book ID required");
        if (!needsBook && bookId!=null) throw new ApiException(400,"Book ID not supported for this command");
        // Preserve the exact legacy body: no new schema fields or internal Catalog IDs.
        String payload="{\"type\":\""+command.type+"\""+(needsBook ? ",\"book_id\":"+bookId : "")+"}";
        var message=MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
            .setContentType(MessageProperties.CONTENT_TYPE_JSON).setDeliveryMode(MessageDeliveryMode.PERSISTENT)
            .setMessageId(UUID.randomUUID().toString()).build();
        var confirmation=new CorrelationData(message.getMessageProperties().getMessageId());
        try {
            rabbit.send("",queue,message,confirmation);
            var result=confirmation.getFuture().get(confirmTimeoutMs,TimeUnit.MILLISECONDS);
            if (!result.ack() || confirmation.getReturned()!=null) throw new IllegalStateException("Not routed and confirmed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(503,"Crawl task delivery interrupted");
        } catch (Exception e) {
            throw new ApiException(503,"Crawl task delivery not confirmed");
        }
    }
}
