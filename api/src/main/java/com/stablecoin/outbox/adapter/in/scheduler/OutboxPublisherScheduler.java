package com.stablecoin.outbox.adapter.in.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.stablecoin.outbox.application.port.in.PublishOutboxUseCase;
import com.stablecoin.outbox.application.port.in.PublishOutboxUseCase.PublishResult;

/**
 * outbox.publisher.interval 마다 발행기를 돌린다. 한 번에 꽉 찬 묶음을 잡았으면 쉬지 않고 바로 다음 묶음을 잡는다.
 * outbox.publisher.enabled=false 면 등록되지 않는다 (테스트 등).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
class OutboxPublisherScheduler {

	private static final Logger log = LoggerFactory.getLogger(OutboxPublisherScheduler.class);

	/** 한 번 실행에서 연속으로 처리할 최대 묶음 수 (다른 작업이 굶지 않도록) */
	private static final int MAX_BATCHES_PER_RUN = 10;

	private final PublishOutboxUseCase publishOutbox;

	OutboxPublisherScheduler(PublishOutboxUseCase publishOutbox) {
		this.publishOutbox = publishOutbox;
	}

	@Scheduled(fixedDelayString = "${outbox.publisher.interval:1s}", initialDelayString = "${outbox.publisher.interval:1s}")
	void run() {
		try {
			for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
				PublishResult result = publishOutbox.publishBatch();
				if (result.claimed() == 0 || result.failed() > 0) {
					return;
				}
			}
		}
		catch (RuntimeException ex) {
			// DB에 연결할 수 없는 경우 등. 다음 주기에 다시 시도한다.
			log.warn("Outbox publisher run failed: {}", ex.toString());
		}
	}

}
