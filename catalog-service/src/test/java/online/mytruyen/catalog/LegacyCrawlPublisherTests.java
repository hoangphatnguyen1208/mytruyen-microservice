package online.mytruyen.catalog;

import online.mytruyen.catalog.exception.ApiException;
import online.mytruyen.catalog.messaging.LegacyCrawlPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static online.mytruyen.catalog.messaging.LegacyCrawlPublisher.Command.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LegacyCrawlPublisherTests {
    @Test void sendsExactOldWorkerMessagesToConfiguredDefaultExchangeQueue() {
        var rabbit=mock(RabbitTemplate.class); var bodies=new ArrayList<String>();
        doAnswer(call->{
            assertThat((String)call.getArgument(0)).isEmpty();
            assertThat((String)call.getArgument(1)).isEqualTo("legacy-crawl-test");
            Message message=call.getArgument(2);
            assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
            bodies.add(new String(message.getBody(),StandardCharsets.UTF_8));
            CorrelationData correlation=call.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true,null)); return null;
        }).when(rabbit).send(anyString(),anyString(),any(Message.class),any(CorrelationData.class));
        var publisher=new LegacyCrawlPublisher(rabbit,true,"legacy-crawl-test",50);
        for (var command:List.of(GENRES,TAGS,STATUSES,ALL_BOOKS)) publisher.publish(command,null);
        publisher.publish(BOOK,123L); publisher.publish(CHAPTERS,123L);
        assertThat(bodies).containsExactly("{\"type\":\"crawl_genres\"}","{\"type\":\"crawl_tags\"}",
            "{\"type\":\"crawl_book_statuses\"}","{\"type\":\"crawl_all_books\"}",
            "{\"type\":\"crawl_book\",\"book_id\":123}","{\"type\":\"crawl_chapters\",\"book_id\":123}");
    }
    @Test void disabledOrInvalidCommandsNeverContactBroker() {
        var rabbit=mock(RabbitTemplate.class);
        assertThatThrownBy(()->new LegacyCrawlPublisher(rabbit,false,"",50).publish(BOOK,123L))
            .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status).isEqualTo(503));
        var publisher=new LegacyCrawlPublisher(rabbit,true,"test",50);
        assertThatThrownBy(()->publisher.publish(BOOK,null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->publisher.publish(CHAPTERS,-1L)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->publisher.publish(GENRES,123L)).isInstanceOf(ApiException.class);
        verifyNoInteractions(rabbit);
    }
    @Test void unconfirmedOrUnroutableTasksNeverReturnSuccess() {
        for (String mode:List.of("nack","returned","offline","timeout")) {
            var rabbit=mock(RabbitTemplate.class);
            doAnswer(call->{
                if (mode.equals("offline")) throw new IllegalStateException("offline");
                CorrelationData correlation=call.getArgument(3);
                if (mode.equals("timeout")) return null;
                if (mode.equals("returned")) correlation.setReturned(new ReturnedMessage(call.getArgument(2),312,"NO_ROUTE","","queue"));
                correlation.getFuture().complete(new CorrelationData.Confirm(!mode.equals("nack"),null)); return null;
            }).when(rabbit).send(anyString(),anyString(),any(Message.class),any(CorrelationData.class));
            assertThatThrownBy(()->new LegacyCrawlPublisher(rabbit,true,"test",5).publish(BOOK,123L))
                .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status).isEqualTo(503));
        }
    }
}
