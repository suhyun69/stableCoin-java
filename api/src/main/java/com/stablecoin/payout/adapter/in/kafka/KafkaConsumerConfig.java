package com.stablecoin.payout.adapter.in.kafka;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * 소비자 처리 실패를 예외 종류로 나눠 처리한다 (stableCoin TS job-failure 의 재시도 / 영구 실패 / 확인 필요 분류).
 * <ul>
 * <li><b>재시도:</b> 일시 장애 (노드·DB 연결 불가, deadlock, 직렬화 실패 등). 같은 메시지를 retry.initial-interval 부터 두 배씩
 * (최대 retry.max-interval 간격, 총 retry.max-elapsed) 다시 처리하고, 그래도 안 되면 DLT로 보낸다 (RETRIES_EXHAUSTED).</li>
 * <li><b>영구 실패:</b> 해석할 수 없는 메시지. 다시 해도 같으므로 바로 DLT로 보낸다 (PERMANENT).</li>
 * <li><b>확인 필요:</b> 그 밖의 예상하지 못한 예외. 안전하게 판단할 수 없으므로 재시도하지 않고 바로 DLT로 보낸다 (REQUIRES_REVIEW).</li>
 * </ul>
 * DLT는 원래 토픽 이름 + ".DLT" (stablecoin.payout.DLT) 이고, 원래 파티션으로 보낸다. 메시지에는 failure-kind 와 예외 정보 헤더가 붙는다.
 * 지급 자체의 판단(보낼 때 거부, 영수증 없음 → REQUIRES_REVIEW)은 지급 상태로 처리하며 여기와는 별개다.
 */
@Configuration
class KafkaConsumerConfig {

	static final String DLT_SUFFIX = ".DLT";

	private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

	@Bean
	DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate,
			@Value("${payout.dispatcher.retry.initial-interval:1s}") Duration initialInterval,
			@Value("${payout.dispatcher.retry.max-interval:30s}") Duration maxInterval,
			@Value("${payout.dispatcher.retry.max-elapsed:5m}") Duration maxElapsed) {
		DeadLetterPublishingRecoverer deadLetter = new DeadLetterPublishingRecoverer(kafkaTemplate,
				(record, ex) -> new TopicPartition(record.topic() + DLT_SUFFIX, record.partition()));
		deadLetter.addHeadersFunction((record, ex) -> {
			RecordHeaders headers = new RecordHeaders();
			headers.add(FailureKind.HEADER, FailureKind.of(ex).name().getBytes(StandardCharsets.UTF_8));
			return headers;
		});
		ExponentialBackOff backOff = new ExponentialBackOff(initialInterval.toMillis(), 2.0);
		backOff.setMaxInterval(maxInterval.toMillis());
		backOff.setMaxElapsedTime(maxElapsed.toMillis());
		DefaultErrorHandler handler = new DefaultErrorHandler((record, ex) -> {
			log.error("Sending {}-{}@{} to {}{} as {}: {}", record.topic(), record.partition(), record.offset(),
					record.topic(), DLT_SUFFIX, FailureKind.of(ex), rootCause(ex).toString());
			deadLetter.accept(record, ex);
		}, backOff);
		// 일시 장애만 재시도하고, 나머지는 모두 바로 DLT로 보낸다
		handler.defaultFalse();
		FailureKind.RETRYABLE.forEach(handler::addRetryableExceptions);
		return handler;
	}

	@Bean
	NewTopic payoutEventsDeadLetterTopic(@Value("${outbox.kafka.topic-prefix}") String topicPrefix) {
		return TopicBuilder.name(topicPrefix + "payout" + DLT_SUFFIX).partitions(3).replicas(1).build();
	}

	private static Throwable rootCause(Throwable ex) {
		Throwable cause = ex;
		while (cause.getCause() != null && cause.getCause() != cause) {
			cause = cause.getCause();
		}
		return cause;
	}

}
