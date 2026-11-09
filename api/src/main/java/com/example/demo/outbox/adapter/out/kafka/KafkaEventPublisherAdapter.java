package com.example.demo.outbox.adapter.out.kafka;
import com.example.demo.outbox.application.port.out.EventPublisherPort;
import com.example.demo.outbox.domain.OutboxMessage;
import com.example.demo.config.MessagingProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import java.util.concurrent.TimeUnit;
@Component
@ConditionalOnProperty(name="stablepay.messaging.enabled",havingValue="true")
public class KafkaEventPublisherAdapter implements EventPublisherPort {
    private final KafkaTemplate<String,String> kafka;
    private final MessagingProperties properties;
    private final String key;
    public KafkaEventPublisherAdapter(KafkaTemplate<String,String> kafka, MessagingProperties properties,
        @Value("${stablepay.chain.id:31337}") long chainId,
        @Value("${stablepay.chain.sender-address:0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266}") String sender) {
        this.kafka=kafka; this.properties=properties; this.key=chainId+":"+sender.toLowerCase(java.util.Locale.ROOT);
    }
    public void publish(OutboxMessage message) {
        try { kafka.send(properties.topic(),key,message.payload()).get(20,TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Kafka publish interrupted",e); }
        catch (Exception e) { throw new IllegalStateException("Kafka publish failed",e); }
    }
}
