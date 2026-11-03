package com.stablecoin.outbox.application;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.stablecoin.outbox.application.port.in.PublishOutboxUseCase;
import com.stablecoin.outbox.application.port.out.OutboxEventPublisherPort;
import com.stablecoin.outbox.application.port.out.OutboxStorePort;
import com.stablecoin.outbox.domain.ClaimedOutboxMessage;

/**
 * outbox 메시지를 한 묶음 잡아서 하나씩 발행하고, 발행이 확인되면 PUBLISHED로 바꾼다.
 * <ul>
 * <li>발행과 PUBLISHED 표시 사이에 죽으면 임대가 끝난 뒤 다시 발행된다 (at-least-once). 받는 쪽이 메시지 ID로 중복을 거른다.</li>
 * <li>발행에 실패하면 에러를 기록하고 1s, 2s, 4s ... (최대 max-backoff) 뒤에 다시 시도한다. max-attempts 번 실패하면 FAILED.</li>
 * <li>한 메시지가 실패해도 같은 묶음의 나머지는 계속 발행한다.</li>
 * </ul>
 */
@Service
public class PublishOutboxBatchService implements PublishOutboxUseCase {

	private static final Logger log = LoggerFactory.getLogger(PublishOutboxBatchService.class);

	private final OutboxStorePort store;

	private final OutboxEventPublisherPort publisher;

	private final Settings settings;

	/** 이 인스턴스를 구분하는 워커 ID (호스트명@PID 와 임의 값) */
	private final String workerId = ManagementFactory.getRuntimeMXBean().getName() + "-"
			+ UUID.randomUUID().toString().substring(0, 8);

	@Autowired
	public PublishOutboxBatchService(OutboxStorePort store, OutboxEventPublisherPort publisher,
			@Value("${outbox.publisher.batch-size:20}") int batchSize,
			@Value("${outbox.publisher.lease:30s}") Duration lease,
			@Value("${outbox.publisher.initial-backoff:1s}") Duration initialBackoff,
			@Value("${outbox.publisher.max-backoff:5m}") Duration maxBackoff,
			@Value("${outbox.publisher.max-attempts:10}") int maxAttempts) {
		this(store, publisher, new Settings(batchSize, lease, initialBackoff, maxBackoff, maxAttempts));
	}

	PublishOutboxBatchService(OutboxStorePort store, OutboxEventPublisherPort publisher, Settings settings) {
		this.store = store;
		this.publisher = publisher;
		this.settings = settings;
	}

	public String workerId() {
		return workerId;
	}

	@Override
	public PublishResult publishBatch() {
		List<ClaimedOutboxMessage> messages = store.claimBatch(workerId, settings.batchSize(), settings.lease());
		int published = 0;
		int failed = 0;
		for (ClaimedOutboxMessage message : messages) {
			try {
				publisher.publish(message);
			}
			catch (RuntimeException ex) {
				failed++;
				fail(message, ex);
				continue;
			}
			if (store.markPublished(message.id(), workerId)) {
				published++;
			}
			else {
				// 발행에 오래 걸려 임대가 끝났고 다른 워커가 가져갔다. 그 워커가 다시 발행하므로 받는 쪽에서 중복을 거른다.
				log.warn("Lost lease on outbox message {} after publishing; it will be published again", message.id());
			}
		}
		if (!messages.isEmpty()) {
			log.info("Outbox batch: claimed={} published={} failed={}", messages.size(), published, failed);
		}
		return new PublishResult(messages.size(), published, failed);
	}

	private void fail(ClaimedOutboxMessage message, RuntimeException ex) {
		boolean giveUp = message.attempts() >= settings.maxAttempts();
		Duration retryAfter = settings.backoff(message.attempts());
		String error = ex.getClass().getSimpleName() + ": " + ex.getMessage();
		store.markFailed(message.id(), workerId, error, retryAfter, giveUp);
		if (giveUp) {
			log.error("Outbox message {} failed {} times; marked FAILED: {}", message.id(), message.attempts(), error);
		}
		else {
			log.warn("Outbox message {} publish failed (attempt {}), retry in {}: {}", message.id(),
					message.attempts(), retryAfter, error);
		}
	}

	record Settings(int batchSize, Duration lease, Duration initialBackoff, Duration maxBackoff, int maxAttempts) {

		/**
		 * attempts번째 실패 뒤 대기 시간: initialBackoff × 2^(attempts-1), 최대 maxBackoff
		 */
		Duration backoff(int attempts) {
			int exponent = Math.min(Math.max(attempts - 1, 0), 30);
			Duration delay = initialBackoff.multipliedBy(1L << exponent);
			return (delay.compareTo(maxBackoff) > 0) ? maxBackoff : delay;
		}

	}

}
