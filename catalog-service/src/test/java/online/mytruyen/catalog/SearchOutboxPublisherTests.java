package online.mytruyen.catalog;

import online.mytruyen.catalog.domain.SearchOutboxEvent;
import online.mytruyen.catalog.messaging.SearchOutboxPublisher;
import online.mytruyen.catalog.repository.SearchOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SearchOutboxPublisherTests {
    @Test void marksOnlyConfirmedAndRoutedMessages() throws Exception {
        for (String outcome : List.of("ack", "nack", "returned", "offline")) {
            var events=mock(SearchOutboxRepository.class);
            var rabbit=mock(RabbitTemplate.class);
            var event=new SearchOutboxEvent();
            event.setEventId(UUID.randomUUID()); event.setBookId(42L);
            when(events.lockPending(any())).thenReturn(List.of(event));
            doAnswer(call -> {
                if (outcome.equals("offline")) throw new IllegalStateException("offline");
                Message message=call.getArgument(2);
                assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
                assertThat(new String(message.getBody(),java.nio.charset.StandardCharsets.UTF_8)).contains("\"book_id\":42");
                CorrelationData correlation=call.getArgument(3);
                if (outcome.equals("returned")) correlation.setReturned(new ReturnedMessage(message,312,"NO_ROUTE","exchange","key"));
                correlation.getFuture().complete(new CorrelationData.Confirm(!outcome.equals("nack"),null));
                return null;
            }).when(rabbit).send(anyString(),anyString(),any(Message.class),any(CorrelationData.class));
            var publisher=new SearchOutboxPublisher(events,rabbit);
            if (outcome.equals("ack")) {
                assertThat(publisher.publishBatch()).isEqualTo(1);
                assertThat(event.getPublishedAt()).isNotNull();
            } else {
                assertThatThrownBy(publisher::publishBatch).isInstanceOf(IllegalStateException.class);
                assertThat(event.getPublishedAt()).isNull();
            }
        }
    }
}
