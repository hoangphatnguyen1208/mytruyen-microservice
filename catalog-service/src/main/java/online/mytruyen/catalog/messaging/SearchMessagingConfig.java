package online.mytruyen.catalog.messaging;

import org.springframework.amqp.core.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "catalog.search-sync.enabled", havingValue = "true")
public class SearchMessagingConfig {
    public static final String EXCHANGE = "mytruyen.catalog.search.v1";
    public static final String QUEUE = "mytruyen.search.sync.v1";

    @Bean Declarables searchTopology() {
        var exchange = new DirectExchange(EXCHANGE, true, false);
        var queue = QueueBuilder.durable(QUEUE)
            .withArgument("x-single-active-consumer", true)
            .deadLetterExchange("").deadLetterRoutingKey(QUEUE + ".dead").build();
        var dead = QueueBuilder.durable(QUEUE + ".dead").build();
        return new Declarables(exchange, queue, dead,
            BindingBuilder.bind(queue).to(exchange).with("book.changed"));
    }
}
