package com.example.demo.integration;
import com.example.demo.testsupport.TestDatabase;
import com.example.demo.outbox.adapter.out.persistence.OutboxPersistenceAdapter;
import com.example.demo.usecase.impl.PublishOutboxUseCaseImpl;
import org.junit.jupiter.api.*;
import java.time.Duration;
import java.util.*;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.springframework.kafka.core.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("kafka")
class OutboxPublisherTest {
    @Test void publishesToKafkaAndRecoversExpiredLeaseWithoutLosingEvent() throws Exception {
        String topic="test-outbox-"+UUID.randomUUID();
        String bootstrap=System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS","localhost:9092");
        try(var admin=Admin.create(Map.of("bootstrap.servers",bootstrap)); var db=new TestDatabase()) {
            admin.createTopics(List.of(new NewTopic(topic,1,(short)1))).all().get();
            var factory=new DefaultKafkaProducerFactory<String,String>(Map.of("bootstrap.servers",bootstrap,"key.serializer","org.apache.kafka.common.serialization.StringSerializer","value.serializer","org.apache.kafka.common.serialization.StringSerializer","acks","all"));
            var template=new KafkaTemplate<String,String>(factory);
            try(var consumer=new KafkaConsumer<String,String>(Map.of("bootstrap.servers",bootstrap,"group.id",UUID.randomUUID().toString(),"auto.offset.reset","earliest","key.deserializer","org.apache.kafka.common.serialization.StringDeserializer","value.deserializer","org.apache.kafka.common.serialization.StringDeserializer"))) {
                String payout=db.payout(); var store=new OutboxPersistenceAdapter(db.jdbc);
                var old=store.claim("old").orElseThrow();
                assertTrue(store.claim("other").isEmpty());
                db.jdbc.update("UPDATE outbox_messages SET locked_at=now()-interval '31 seconds'");
                var current=store.claim("new").orElseThrow();
                assertFalse(store.published(old));
                template.send(topic,"31337:sender",current.payload()).get();
                // Simulate publish success followed by process crash before DB acknowledgement.
                db.jdbc.update("UPDATE outbox_messages SET locked_at=now()-interval '31 seconds'");
                var publisher=new PublishOutboxUseCaseImpl(store,m -> { try {template.send(topic,"31337:sender",m.payload()).get();}catch(Exception e){throw new IllegalStateException(e);} });
                assertEquals(1,publisher.publish());
                assertEquals("PUBLISHED",db.jdbc.queryForObject("SELECT status FROM outbox_messages WHERE aggregate_id=?",String.class,payout));
                consumer.subscribe(List.of(topic));
                var ids=new ArrayList<String>(); long deadline=System.nanoTime()+Duration.ofSeconds(15).toNanos();
                while(ids.size()<2 && System.nanoTime()<deadline) consumer.poll(Duration.ofMillis(200)).forEach(record -> ids.add(record.value()));
                assertEquals(2,ids.size()); assertEquals(ids.get(0),ids.get(1));
                db.payout();
                var failing=new PublishOutboxUseCaseImpl(store,m -> {throw new IllegalStateException("broker offline");});
                assertEquals(0,failing.publish());
                assertEquals(1,db.jdbc.queryForObject("SELECT count(*) FROM outbox_messages WHERE status='PENDING' AND last_error IS NOT NULL AND locked_at IS NULL",Integer.class));
            } finally { factory.destroy(); admin.deleteTopics(List.of(topic)).all().get(); }
        }
    }
}
