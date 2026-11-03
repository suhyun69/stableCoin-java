package com.stablecoin.payout.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.testcontainers.kafka.KafkaContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;

import com.stablecoin.TestcontainersConfiguration;
import com.stablecoin.chain.application.ChainUnavailableException;
import com.stablecoin.payout.application.port.in.DispatchPayoutUseCase;

/**
 * 실제 Kafka로 소비자의 오류 분류를 확인한다: 일시 장애는 재시도, 깨진 메시지와 예상하지 못한 오류는 바로 DLT.
 */
@SpringBootTest(properties = { "chain.rpc-url=http://127.0.0.1:1", "outbox.publisher.enabled=false",
		"payout.dispatcher.enabled=true", "payout.confirmation.interval=1h" })
@Import(TestcontainersConfiguration.class)
class ConsumerErrorHandlingTest {

	private static final String TOPIC = "stablecoin.payout";

	private static final String DLT = "stablecoin.payout.DLT";

	@MockitoBean
	private DispatchPayoutUseCase dispatchPayout;

	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;

	@Autowired
	private KafkaContainer kafka;

	@Test
	void malformedMessageGoesStraightToTheDeadLetterTopic() throws Exception {
		String key = "malformed-" + UUID.randomUUID();

		send(key, "PAYOUT_REQUESTED", "{not json");

		ConsumerRecord<String, String> dead = consumeFromDlt(key);
		assertThat(dead.value()).isEqualTo("{not json");
		assertThat(header(dead, "kafka_dlt-exception-cause-fqcn"))
			.isEqualTo(InvalidPayoutMessageException.class.getName());
		verify(dispatchPayout, never()).dispatch(any(UUID.class), anyString());
	}

	@Test
	void messageWithoutPayoutIdGoesStraightToTheDeadLetterTopic() throws Exception {
		String key = "no-payout-id-" + UUID.randomUUID();

		send(key, "PAYOUT_REQUESTED", "{\"messageId\":\"m\",\"payload\":{}}");

		assertThat(consumeFromDlt(key)).isNotNull();
		verify(dispatchPayout, never()).dispatch(any(UUID.class), anyString());
	}

	@Test
	void unexpectedErrorIsNotRetriedAndGoesToTheDeadLetterTopic() throws Exception {
		UUID payoutId = UUID.randomUUID();
		willThrow(new IllegalStateException("unexpected state")).given(dispatchPayout)
			.dispatch(org.mockito.ArgumentMatchers.eq(payoutId), anyString());

		send(payoutId.toString(), "PAYOUT_REQUESTED", envelope(payoutId));

		ConsumerRecord<String, String> dead = consumeFromDlt(payoutId.toString());
		assertThat(header(dead, "kafka_dlt-exception-cause-fqcn")).isEqualTo(IllegalStateException.class.getName());
		verify(dispatchPayout, times(1)).dispatch(org.mockito.ArgumentMatchers.eq(payoutId), anyString());
	}

	@Test
	void temporaryFailureIsRetriedAndNotSentToTheDeadLetterTopic() throws Exception {
		UUID payoutId = UUID.randomUUID();
		AtomicInteger calls = new AtomicInteger();
		willAnswer(call -> {
			if (calls.incrementAndGet() == 1) {
				throw new ChainUnavailableException(new ResourceAccessException("Connection refused"));
			}
			return null;
		}).given(dispatchPayout).dispatch(org.mockito.ArgumentMatchers.eq(payoutId), anyString());

		send(payoutId.toString(), "PAYOUT_REQUESTED", envelope(payoutId));

		verify(dispatchPayout, timeout(15_000).times(2)).dispatch(org.mockito.ArgumentMatchers.eq(payoutId),
				anyString());
		assertThat(findInDlt(payoutId.toString(), Duration.ofSeconds(3))).isEmpty();
	}

	@Test
	void otherEventTypesAreIgnored() throws Exception {
		UUID payoutId = UUID.randomUUID();

		send(payoutId.toString(), "MERCHANT_PAYOUT_SETTLED", envelope(payoutId));
		// 그 뒤에 보낸 PAYOUT_REQUESTED 가 처리될 때까지 기다려 앞 메시지를 지나쳤는지 확인한다
		UUID next = UUID.randomUUID();
		send(next.toString(), "PAYOUT_REQUESTED", envelope(next));

		verify(dispatchPayout, timeout(15_000)).dispatch(org.mockito.ArgumentMatchers.eq(next), anyString());
		verify(dispatchPayout, never()).dispatch(org.mockito.ArgumentMatchers.eq(payoutId), anyString());
	}

	private void send(String key, String type, String value) throws Exception {
		ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, key, value);
		record.headers().add("event-type", type.getBytes(StandardCharsets.UTF_8));
		record.headers().add("outbox-message-id", UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
		kafkaTemplate.send(record).get();
	}

	private static String envelope(UUID payoutId) {
		return """
				{"messageId":"%s","type":"PAYOUT_REQUESTED","aggregateType":"PAYOUT","aggregateId":"%s",
				 "payload":{"payoutId":"%s","recipientAddress":"0x70997970c51812dc3a010c7d01b50e0d17dc79c8","amountWei":"1"}}
				""".formatted(UUID.randomUUID(), payoutId, payoutId);
	}

	private ConsumerRecord<String, String> consumeFromDlt(String key) {
		return findInDlt(key, Duration.ofSeconds(20))
			.orElseThrow(() -> new AssertionError("no record with key " + key + " on " + DLT));
	}

	private Optional<ConsumerRecord<String, String>> findInDlt(String key, Duration timeout) {
		Properties props = new Properties();
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
		props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-test-" + UUID.randomUUID());
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
			consumer.subscribe(List.of(DLT));
			long deadline = System.nanoTime() + timeout.toNanos();
			while (System.nanoTime() < deadline) {
				for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
					if (key.equals(record.key())) {
						return Optional.of(record);
					}
				}
			}
			return Optional.empty();
		}
	}

	private static String header(ConsumerRecord<String, String> record, String name) {
		return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
	}

}
