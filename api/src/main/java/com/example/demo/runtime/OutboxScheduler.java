package com.example.demo.runtime;
import com.example.demo.usecase.PublishOutboxUseCase;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
@Component
@ConditionalOnProperty(name={"stablepay.messaging.enabled","stablepay.workers.enabled"},havingValue="true")
public class OutboxScheduler {
    private final PublishOutboxUseCase publish;
    public OutboxScheduler(PublishOutboxUseCase publish) {this.publish=publish;}
    @Scheduled(fixedDelayString="${stablepay.workers.outbox-delay-ms:1000}")
    public void publish() { publish.publish(); }
}
