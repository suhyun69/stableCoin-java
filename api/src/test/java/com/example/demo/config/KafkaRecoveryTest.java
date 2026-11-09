package com.example.demo.config;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class KafkaRecoveryTest {
    @SuppressWarnings("unchecked")
    @Test void dltPublishFailurePropagatesInsteadOfAcknowledgingOriginalRecord() {
        KafkaTemplate<String,String> kafka=mock(KafkaTemplate.class);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        var recoverer=new KafkaConfig().payoutRecoverer(kafka,new MessagingProperties("test","group",1));
        recoverer.setVerifyPartition(false);
        assertThrows(RuntimeException.class,() -> recoverer.accept(new ConsumerRecord<>("test",0,0,"key","value"),new IllegalStateException("original")));
        verify(kafka).send(any(ProducerRecord.class));
    }
}
