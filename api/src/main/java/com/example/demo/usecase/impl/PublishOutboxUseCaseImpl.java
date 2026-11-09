package com.example.demo.usecase.impl;
import com.example.demo.usecase.PublishOutboxUseCase;
import com.example.demo.outbox.application.port.out.*;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.*;
import java.util.UUID;
@Service
@ConditionalOnProperty(name="stablepay.messaging.enabled",havingValue="true")
@Transactional(propagation=Propagation.NOT_SUPPORTED)
public class PublishOutboxUseCaseImpl implements PublishOutboxUseCase {
    private final OutboxStorePort store;
    private final EventPublisherPort publisher;
    public PublishOutboxUseCaseImpl(OutboxStorePort store, EventPublisherPort publisher) { this.store=store; this.publisher=publisher; }
    public int publish() {
        int count=0;
        for (int i=0;i<20;i++) {
            var candidate=store.claim(UUID.randomUUID().toString());
            if (candidate.isEmpty()) break;
            var message=candidate.get();
            try { publisher.publish(message); if(store.published(message)) count++; }
            catch (RuntimeException e) { store.failed(message,e.toString()); }
        }
        return count;
    }
}
