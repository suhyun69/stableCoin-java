package com.stablecoin.payout.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import com.stablecoin.TestcontainersConfiguration;
import com.stablecoin.outbox.application.port.out.OutboxPort;
import com.stablecoin.payout.application.port.in.CreatePayoutUseCase.CreatePayoutCommand;
import com.stablecoin.payout.application.port.in.CreatePayoutUseCase.CreatePayoutResult;
import com.stablecoin.payout.application.port.out.PayoutRepositoryPort;
import com.stablecoin.payout.application.port.out.PayoutStatusHistoryPort;
import com.stablecoin.payout.domain.PayoutStatus;

/**
 * 실제 Postgres(Testcontainers)에서 지급, 이력, outbox가 한 트랜잭션으로 저장되는지와 멱등성을 확인한다.
 */
@SpringBootTest(properties = { "chain.rpc-url=http://127.0.0.1:1", "outbox.publisher.enabled=false",
		"payout.dispatcher.enabled=false" })
@Import(TestcontainersConfiguration.class)
class CreatePayoutWithOutboxServiceTest {

	private static final String RECIPIENT = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";

	private static final BigInteger AMOUNT = new BigInteger("25500000000000000000");

	private static final CreatePayoutCommand COMMAND = new CreatePayoutCommand("merchant_demo_01", RECIPIENT, AMOUNT,
			"payout-2026-005");

	@Autowired
	private CreatePayoutWithOutboxService service;

	@Autowired
	private PayoutRepositoryPort payouts;

	@Autowired
	private PayoutStatusHistoryPort history;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void cleanTables() {
		jdbc.update("DELETE FROM outbox_messages");
		jdbc.update("DELETE FROM processed_messages");
		jdbc.update("DELETE FROM chain_transactions");
		jdbc.update("DELETE FROM payout_dispatches");
		jdbc.update("DELETE FROM payout_status_history");
		jdbc.update("DELETE FROM payouts");
	}

	@Test
	void storesPayoutHistoryAndOutboxMessageTogether() {
		CreatePayoutResult result = service.create(COMMAND);

		assertThat(result.replayed()).isFalse();
		assertThat(result.payout().status()).isEqualTo(PayoutStatus.REQUESTED);
		assertThat(counts()).containsExactly(1, 1, 1);
		assertThat(jdbc.queryForMap("SELECT from_status, to_status, reason FROM payout_status_history"))
			.containsEntry("from_status", null)
			.containsEntry("to_status", "REQUESTED")
			.containsEntry("reason", "PAYOUT_CREATED");
		assertThat(jdbc.queryForMap("""
				SELECT aggregate_type, aggregate_id::text AS aggregate_id, type, status, attempts,
				       payload->>'payoutId' AS payout_id, payload->>'recipientAddress' AS recipient,
				       payload->>'amountWei' AS amount_wei
				FROM outbox_messages"""))
			.containsEntry("aggregate_type", "PAYOUT")
			.containsEntry("aggregate_id", result.payout().id().toString())
			.containsEntry("type", "PAYOUT_REQUESTED")
			.containsEntry("status", "PENDING")
			.containsEntry("attempts", 0)
			.containsEntry("payout_id", result.payout().id().toString())
			.containsEntry("recipient", RECIPIENT)
			.containsEntry("amount_wei", "25500000000000000000");
	}

	@Test
	void rollsBackPayoutAndHistoryWhenOutboxInsertFails() {
		OutboxPort failingOutbox = message -> {
			throw new IllegalStateException("outbox insert failed");
		};
		CreatePayoutWithOutboxService failing = new CreatePayoutWithOutboxService(payouts, history, failingOutbox,
				transactionManager);

		assertThatThrownBy(() -> failing.create(COMMAND)).hasMessage("outbox insert failed");

		assertThat(counts()).containsExactly(0, 0, 0);
	}

	@Test
	void retryWithSameKeyAndBodyReturnsTheFirstPayout() {
		CreatePayoutResult first = service.create(COMMAND);
		// 주소 대소문자만 다른 같은 요청
		CreatePayoutResult retry = service.create(new CreatePayoutCommand("merchant_demo_01",
				"0x70997970C51812dc3A010C7d01b50e0d17dc79C8", AMOUNT, "payout-2026-005"));

		assertThat(retry.replayed()).isTrue();
		assertThat(retry.payout().id()).isEqualTo(first.payout().id());
		assertThat(counts()).containsExactly(1, 1, 1);
	}

	@Test
	void sameKeyWithDifferentBodyIsRejected() {
		service.create(COMMAND);

		assertThatThrownBy(() -> service.create(new CreatePayoutCommand("merchant_demo_01", RECIPIENT,
				AMOUNT.add(BigInteger.ONE), "payout-2026-005")))
			.isInstanceOf(IdempotencyKeyConflictException.class);

		assertThat(counts()).containsExactly(1, 1, 1);
	}

	@Test
	void concurrentRequestsWithSameKeyCreateOnePayout() throws Exception {
		int requests = 8;
		ExecutorService pool = Executors.newFixedThreadPool(requests);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<CreatePayoutResult>> results = new ArrayList<>();
		try {
			for (int i = 0; i < requests; i++) {
				Callable<CreatePayoutResult> call = () -> {
					start.await();
					return service.create(COMMAND);
				};
				results.add(pool.submit(call));
			}
			start.countDown();
			List<CreatePayoutResult> done = new ArrayList<>();
			for (Future<CreatePayoutResult> result : results) {
				done.add(result.get());
			}

			assertThat(done).filteredOn(result -> !result.replayed()).hasSize(1);
			assertThat(done).extracting(result -> result.payout().id()).containsOnly(done.get(0).payout().id());
			assertThat(counts()).containsExactly(1, 1, 1);
		}
		finally {
			pool.shutdownNow();
		}
	}

	/** payouts, payout_status_history, outbox_messages 행 수 */
	private List<Integer> counts() {
		return List.of(count("payouts"), count("payout_status_history"), count("outbox_messages"));
	}

	private int count(String table) {
		return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
	}

}
