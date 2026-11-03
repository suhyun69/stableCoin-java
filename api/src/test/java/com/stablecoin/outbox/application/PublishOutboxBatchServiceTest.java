package com.stablecoin.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.stablecoin.TestcontainersConfiguration;
import com.stablecoin.outbox.application.PublishOutboxBatchService.Settings;
import com.stablecoin.outbox.application.port.in.PublishOutboxUseCase.PublishResult;
import com.stablecoin.outbox.application.port.out.OutboxEventPublisherPort;
import com.stablecoin.outbox.application.port.out.OutboxStorePort;
import com.stablecoin.outbox.domain.ClaimedOutboxMessage;
import com.stablecoin.payout.application.port.in.CreatePayoutUseCase;
import com.stablecoin.payout.application.port.in.CreatePayoutUseCase.CreatePayoutCommand;

/**
 * 실제 Postgres, Kafka(Testcontainers)로 outbox 발행기를 확인한다. 스케줄러는 끄고 직접 호출한다.
 */
@SpringBootTest(properties = { "chain.rpc-url=http://127.0.0.1:1", "outbox.publisher.enabled=false" })
@Import(TestcontainersConfiguration.class)
class PublishOutboxBatchServiceTest {

	private static final String TOPIC = "stablecoin.payout";

	private static final String RECIPIENT = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";

	private static final Settings SETTINGS = new Settings(20, Duration.ofSeconds(30), Duration.ofSeconds(1),
			Duration.ofMinutes(5), 10);

	@Autowired
	private PublishOutboxBatchService publisher;

	@Autowired
	private OutboxStorePort store;

	@Autowired
	private CreatePayoutUseCase createPayout;

	@Autowired
	private KafkaContainer kafka;

	@Autowired
	private JsonMapper jsonMapper;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void cleanTables() {
		jdbc.update("DELETE FROM outbox_messages");
		jdbc.update("DELETE FROM payout_status_history");
		jdbc.update("DELETE FROM payouts");
	}

	@Test
	void publishesToKafkaThenMarksPublished() {
		UUID payoutId = createPayout("payout-pub-1");

		PublishResult result = publisher.publishBatch();

		assertThat(result).isEqualTo(new PublishResult(1, 1, 0));
		Map<String, Object> row = outboxRow(payoutId);
		assertThat(row).containsEntry("status", "PUBLISHED")
			.containsEntry("attempts", 1)
			.containsEntry("locked_at", null)
			.containsEntry("locked_by", null)
			.containsEntry("last_error", null);
		assertThat(row.get("published_at")).isNotNull();

		ConsumerRecord<String, String> record = consumeOne(payoutId.toString());
		assertThat(header(record, "outbox-message-id")).isEqualTo(row.get("id").toString());
		assertThat(header(record, "event-type")).isEqualTo("PAYOUT_REQUESTED");
		JsonNode value = jsonMapper.readTree(record.value());
		assertThat(value.get("messageId").asString()).isEqualTo(row.get("id").toString());
		assertThat(value.get("type").asString()).isEqualTo("PAYOUT_REQUESTED");
		assertThat(value.get("aggregateType").asString()).isEqualTo("PAYOUT");
		assertThat(value.get("aggregateId").asString()).isEqualTo(payoutId.toString());
		assertThat(value.get("payload").get("payoutId").asString()).isEqualTo(payoutId.toString());
		assertThat(value.get("payload").get("recipientAddress").asString()).isEqualTo(RECIPIENT);
		assertThat(value.get("payload").get("amountWei").asString()).isEqualTo("25500000000000000000");
	}

	@Test
	void publishedMessagesAreNotPublishedAgain() {
		createPayout("payout-pub-2");
		publisher.publishBatch();

		assertThat(publisher.publishBatch()).isEqualTo(new PublishResult(0, 0, 0));
	}

	@Test
	void failedPublishRecordsErrorAndWaitsBeforeRetry() {
		UUID payoutId = createPayout("payout-fail-1");
		PublishOutboxBatchService failing = new PublishOutboxBatchService(store, failingPublisher(), SETTINGS);

		assertThat(failing.publishBatch()).isEqualTo(new PublishResult(1, 0, 1));

		Map<String, Object> row = outboxRow(payoutId);
		assertThat(row).containsEntry("status", "PENDING")
			.containsEntry("attempts", 1)
			.containsEntry("locked_at", null)
			.containsEntry("locked_by", null);
		assertThat((String) row.get("last_error")).contains("kafka is down");
		assertThat(jdbc.queryForObject("SELECT available_at > now() FROM outbox_messages", Boolean.class)).isTrue();
		// 대기 시간이 지나기 전에는 다시 잡지 않는다
		assertThat(failing.publishBatch().claimed()).isZero();
	}

	@Test
	void givesUpAfterMaxAttempts() {
		UUID payoutId = createPayout("payout-fail-2");
		PublishOutboxBatchService failing = new PublishOutboxBatchService(store, failingPublisher(),
				new Settings(20, Duration.ofSeconds(30), Duration.ZERO, Duration.ZERO, 2));

		failing.publishBatch();
		assertThat(outboxRow(payoutId)).containsEntry("status", "PENDING").containsEntry("attempts", 1);
		failing.publishBatch();

		assertThat(outboxRow(payoutId)).containsEntry("status", "FAILED").containsEntry("attempts", 2);
		assertThat(failing.publishBatch().claimed()).isZero();
	}

	@Test
	void oneFailureDoesNotStopTheRestOfTheBatch() {
		UUID bad = createPayout("payout-batch-bad");
		createPayout("payout-batch-ok-1");
		createPayout("payout-batch-ok-2");
		OutboxEventPublisherPort failsForOne = message -> {
			if (message.aggregateId().equals(bad)) {
				throw new IllegalStateException("kafka is down");
			}
		};

		PublishResult result = new PublishOutboxBatchService(store, failsForOne, SETTINGS).publishBatch();

		assertThat(result).isEqualTo(new PublishResult(3, 2, 1));
		assertThat(outboxRow(bad)).containsEntry("status", "PENDING");
	}

	@Test
	void leaseKeepsOtherWorkersAwayUntilItExpires() {
		UUID payoutId = createPayout("payout-lease");

		List<ClaimedOutboxMessage> first = store.claimBatch("worker-a", 20, Duration.ofSeconds(30));
		assertThat(first).hasSize(1);
		// worker-a 가 잡고 있는 동안 worker-b 는 잡지 못한다
		assertThat(store.claimBatch("worker-b", 20, Duration.ofSeconds(30))).isEmpty();

		// worker-a 가 죽어서 임대가 끝났다
		jdbc.update("UPDATE outbox_messages SET locked_at = now() - interval '31 seconds'");
		List<ClaimedOutboxMessage> reclaimed = store.claimBatch("worker-b", 20, Duration.ofSeconds(30));

		assertThat(reclaimed).extracting(ClaimedOutboxMessage::id).containsExactly(first.get(0).id());
		assertThat(reclaimed.get(0).attempts()).isEqualTo(2);
		// 늦게 돌아온 worker-a 는 PUBLISHED 로 바꾸지 못한다
		assertThat(store.markPublished(first.get(0).id(), "worker-a")).isFalse();
		assertThat(store.markPublished(first.get(0).id(), "worker-b")).isTrue();
		assertThat(outboxRow(payoutId)).containsEntry("status", "PUBLISHED");
	}

	@Test
	void concurrentWorkersClaimDifferentMessages() throws Exception {
		for (int i = 0; i < 30; i++) {
			createPayout("payout-concurrent-" + i);
		}
		CountDownLatch start = new CountDownLatch(1);
		CompletableFuture<List<ClaimedOutboxMessage>> a = CompletableFuture.supplyAsync(() -> claimAfter(start, "worker-a"));
		CompletableFuture<List<ClaimedOutboxMessage>> b = CompletableFuture.supplyAsync(() -> claimAfter(start, "worker-b"));
		start.countDown();

		List<UUID> claimed = new ArrayList<>();
		a.get().forEach(message -> claimed.add(message.id()));
		b.get().forEach(message -> claimed.add(message.id()));

		assertThat(claimed).hasSize(30).doesNotHaveDuplicates();
		assertThat(a.get().size()).isLessThanOrEqualTo(20);
		assertThat(b.get().size()).isLessThanOrEqualTo(20);
	}

	@Test
	void backoffDoublesUpToTheMaximum() {
		Settings settings = new Settings(20, Duration.ofSeconds(30), Duration.ofSeconds(1), Duration.ofSeconds(10), 10);

		assertThat(List.of(1, 2, 3, 4, 5, 20).stream().map(settings::backoff).toList()).containsExactly(
				Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4), Duration.ofSeconds(8),
				Duration.ofSeconds(10), Duration.ofSeconds(10));
	}

	private List<ClaimedOutboxMessage> claimAfter(CountDownLatch start, String workerId) {
		try {
			start.await();
		}
		catch (InterruptedException ex) {
			throw new IllegalStateException(ex);
		}
		return store.claimBatch(workerId, 20, Duration.ofSeconds(30));
	}

	private UUID createPayout(String idempotencyKey) {
		return createPayout.create(new CreatePayoutCommand("merchant_demo_01", RECIPIENT,
				new BigInteger("25500000000000000000"), idempotencyKey))
			.payout()
			.id();
	}

	private Map<String, Object> outboxRow(UUID payoutId) {
		return jdbc.queryForMap("""
				SELECT id, status, attempts, locked_at, locked_by, published_at, last_error
				FROM outbox_messages WHERE aggregate_id = ?""", payoutId);
	}

	private static OutboxEventPublisherPort failingPublisher() {
		return message -> {
			throw new IllegalStateException("kafka is down");
		};
	}

	private ConsumerRecord<String, String> consumeOne(String key) {
		Properties props = new Properties();
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
		props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
			consumer.subscribe(List.of(TOPIC));
			long deadline = System.currentTimeMillis() + 15_000;
			Set<String> seen = new HashSet<>();
			while (System.currentTimeMillis() < deadline) {
				for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
					seen.add(record.key());
					if (key.equals(record.key())) {
						return record;
					}
				}
			}
			throw new AssertionError("no record with key " + key + " on " + TOPIC + " (seen " + seen + ")");
		}
	}

	private static String header(ConsumerRecord<String, String> record, String name) {
		return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
	}

}
