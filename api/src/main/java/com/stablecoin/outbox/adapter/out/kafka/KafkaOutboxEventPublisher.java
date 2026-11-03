package com.stablecoin.outbox.adapter.out.kafka;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.producer.ProducerRecord;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.stablecoin.outbox.application.port.out.OutboxEventPublisherPort;
import com.stablecoin.outbox.domain.ClaimedOutboxMessage;

/**
 * outbox 메시지를 Kafka로 보내는 {@link OutboxEventPublisherPort} 구현.
 * <ul>
 * <li>토픽: topic-prefix + aggregate_type 소문자 (예: stablecoin.payout)</li>
 * <li>키: aggregate_id. 같은 지급의 이벤트는 같은 파티션으로 가서 순서가 지켜진다.</li>
 * <li>값: {messageId, type, aggregateType, aggregateId, payload} JSON</li>
 * <li>헤더: outbox-message-id, event-type. 받는 쪽은 outbox-message-id로 중복을 거른다.</li>
 * </ul>
 * 브로커가 받았다고 확인(acks=all)할 때까지 기다린 뒤 돌아온다.
 */
@Component
class KafkaOutboxEventPublisher implements OutboxEventPublisherPort {

	static final String HEADER_MESSAGE_ID = "outbox-message-id";

	static final String HEADER_EVENT_TYPE = "event-type";

	private static final Duration SEND_TIMEOUT = Duration.ofSeconds(15);

	private final KafkaTemplate<String, String> kafkaTemplate;

	private final JsonMapper jsonMapper;

	private final String topicPrefix;

	KafkaOutboxEventPublisher(KafkaTemplate<String, String> kafkaTemplate, JsonMapper jsonMapper,
			@Value("${outbox.kafka.topic-prefix}") String topicPrefix) {
		this.kafkaTemplate = kafkaTemplate;
		this.jsonMapper = jsonMapper;
		this.topicPrefix = topicPrefix;
	}

	static String topicFor(String topicPrefix, String aggregateType) {
		return topicPrefix + aggregateType.toLowerCase(Locale.ROOT);
	}

	@Override
	public void publish(ClaimedOutboxMessage message) {
		ProducerRecord<String, String> record = new ProducerRecord<>(topicFor(topicPrefix, message.aggregateType()),
				message.aggregateId().toString(), envelope(message));
		record.headers().add(HEADER_MESSAGE_ID, message.id().toString().getBytes(StandardCharsets.UTF_8));
		record.headers().add(HEADER_EVENT_TYPE, message.type().getBytes(StandardCharsets.UTF_8));
		try {
			kafkaTemplate.send(record).get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrupted while publishing outbox message " + message.id(), ex);
		}
		catch (ExecutionException ex) {
			throw new IllegalStateException("kafka send failed: " + ex.getCause().getMessage(), ex.getCause());
		}
		catch (TimeoutException ex) {
			throw new IllegalStateException("kafka send timed out after " + SEND_TIMEOUT.toSeconds() + "s", ex);
		}
	}

	private String envelope(ClaimedOutboxMessage message) {
		Map<String, Object> envelope = new LinkedHashMap<>();
		envelope.put("messageId", message.id().toString());
		envelope.put("type", message.type());
		envelope.put("aggregateType", message.aggregateType());
		envelope.put("aggregateId", message.aggregateId().toString());
		envelope.put("payload", jsonMapper.readTree(message.payloadJson()));
		return jsonMapper.writeValueAsString(envelope);
	}

}
