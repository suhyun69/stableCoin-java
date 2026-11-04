package com.stablecoin.payout.adapter.in.kafka;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.stablecoin.payout.application.port.in.DispatchPayoutUseCase;

/**
 * stablecoin.payout 토픽에서 PAYOUT_REQUESTED 이벤트를 받아 지급을 보낸다. 다른 종류의 이벤트(가맹점 알림 등)는 건너뛴다.
 * 같은 이벤트를 여러 번 받아도 된다: 전송은 지급 ID로 한 번만 일어난다.
 * 처리 중 예외가 나면 KafkaConsumerConfig 의 분류에 따라 다시 받거나 DLT(stablecoin.payout.DLT)로 보낸다.
 */
@Component
class PayoutRequestedListener {

	static final String PAYOUT_REQUESTED = "PAYOUT_REQUESTED";

	private static final Logger log = LoggerFactory.getLogger(PayoutRequestedListener.class);

	private final DispatchPayoutUseCase dispatchPayout;

	private final JsonMapper jsonMapper;

	PayoutRequestedListener(DispatchPayoutUseCase dispatchPayout, JsonMapper jsonMapper) {
		this.dispatchPayout = dispatchPayout;
		this.jsonMapper = jsonMapper;
	}

	@KafkaListener(id = "payout-dispatcher", topics = "${outbox.kafka.topic-prefix}payout",
			groupId = "${payout.dispatcher.group-id:stablecoin-payout-dispatcher}",
			autoStartup = "${payout.dispatcher.enabled:true}")
	void onMessage(ConsumerRecord<String, String> record) {
		String type = header(record, "event-type");
		if (!PAYOUT_REQUESTED.equals(type)) {
			return;
		}
		UUID payoutId;
		String messageId;
		try {
			JsonNode envelope = jsonMapper.readTree(record.value());
			payoutId = UUID.fromString(envelope.get("payload").get("payoutId").asString());
			messageId = Objects.requireNonNullElseGet(header(record, "outbox-message-id"),
					() -> envelope.get("messageId").asString());
		}
		catch (RuntimeException ex) {
			throw new InvalidKafkaMessageException("invalid " + PAYOUT_REQUESTED + " message at " + record.topic()
					+ "-" + record.partition() + "@" + record.offset() + ": " + ex, ex);
		}
		log.info("Received {} {} for payout {}", type, messageId, payoutId);
		dispatchPayout.dispatch(payoutId, messageId);
	}

	private static String header(ConsumerRecord<String, String> record, String name) {
		Header header = record.headers().lastHeader(name);
		return (header != null) ? new String(header.value(), StandardCharsets.UTF_8) : null;
	}

}
