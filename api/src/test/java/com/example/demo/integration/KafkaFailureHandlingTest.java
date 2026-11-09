package com.example.demo.integration;
import com.example.demo.config.*;
import com.example.demo.runtime.PayoutKafkaListener;
import com.example.demo.usecase.SendPayoutUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import java.util.*;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@Tag("kafka")
class KafkaFailureHandlingTest {
    @Test void permanentErrorsGoToDltAndTransientErrorsRetryFiveTimes() throws Exception {
        String topic="test-failures-"+UUID.randomUUID(); var settings=new MessagingProperties(topic,"test-group-"+UUID.randomUUID(),1);
        String bootstrap=System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS","localhost:9092");
        var producer=new DefaultKafkaProducerFactory<String,String>(Map.of("bootstrap.servers",bootstrap,"key.serializer","org.apache.kafka.common.serialization.StringSerializer","value.serializer","org.apache.kafka.common.serialization.StringSerializer"));
        var kafka=new KafkaTemplate<String,String>(producer);
        var consumerProperties=Map.<String,Object>of("bootstrap.servers",bootstrap,"group.id",settings.group(),"enable.auto.commit",false,"auto.offset.reset","earliest","key.deserializer","org.apache.kafka.common.serialization.StringDeserializer","value.deserializer","org.apache.kafka.common.serialization.StringDeserializer");
        var factory=new DefaultKafkaConsumerFactory<String,String>(consumerProperties);
        var containerProperties=new ContainerProperties(topic);containerProperties.setGroupId(settings.group());containerProperties.setAckMode(ContainerProperties.AckMode.RECORD);
        var sender=mock(SendPayoutUseCase.class); doThrow(new IllegalStateException("temporary")).when(sender).send(anyString(),anyString());
        var listener=new PayoutKafkaListener(new ObjectMapper(),sender);
        containerProperties.setMessageListener((MessageListener<String,String>)record -> listener.receive(record.value()));
        var container=new ConcurrentMessageListenerContainer<>(factory,containerProperties);
        var config=new KafkaConfig();container.setCommonErrorHandler(config.payoutErrorHandler(config.payoutRecoverer(kafka,settings)));
        try(var admin=Admin.create(Map.of("bootstrap.servers",bootstrap));
            var dlt=new KafkaConsumer<String,String>(Map.of("bootstrap.servers",bootstrap,"group.id",UUID.randomUUID().toString(),"auto.offset.reset","earliest","key.deserializer","org.apache.kafka.common.serialization.StringDeserializer","value.deserializer","org.apache.kafka.common.serialization.StringDeserializer"))) {
            admin.createTopics(List.of(new NewTopic(topic,1,(short)1),new NewTopic(settings.deadLetterTopic(),1,(short)1))).all().get();
            container.start();dlt.subscribe(List.of(settings.deadLetterTopic()));
            kafka.send(topic,"sender","not-json").get();
            kafka.send(topic,"sender","{\"eventId\":\"out_1\",\"eventType\":\"PAYOUT_REQUESTED\",\"schemaVersion\":1,\"payoutId\":\"pay_1\"}").get();
            var values=new ArrayList<String>();long deadline=System.nanoTime()+Duration.ofSeconds(30).toNanos();
            while(values.size()<2 && System.nanoTime()<deadline) dlt.poll(Duration.ofMillis(200)).forEach(record -> values.add(record.value()));
            assertEquals(2,values.size());assertEquals("not-json",values.getFirst());
            verify(sender,times(5)).send("out_1","pay_1");
            container.stop();admin.deleteTopics(List.of(topic,settings.deadLetterTopic())).all().get();
        } finally {container.stop();producer.destroy();}
    }
}
