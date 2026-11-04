package com.stablecoin.payout;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.stablecoin.TestcontainersConfiguration;
import com.stablecoin.chain.application.port.in.ChainQueryUseCase;
import com.stablecoin.payout.application.port.in.CreatePayoutUseCase;
import com.stablecoin.payout.application.port.in.CreatePayoutUseCase.CreatePayoutCommand;
import com.stablecoin.payout.application.port.in.DispatchPayoutUseCase;
import com.stablecoin.payout.application.port.in.RunReconciliationUseCase;

/**
 * 실제 Postgres, Kafka, anvil(컨테이너)로 지급 전체 흐름을 확인한다:
 * 지급 요청 → outbox → Kafka → 소비자가 체인 전송 → 영수증 확인 → SETTLED + 가맹점 알림.
 */
@Testcontainers
@SpringBootTest(properties = { "outbox.publisher.interval=200ms", "payout.confirmation.interval=200ms" })
@Import(TestcontainersConfiguration.class)
class PayoutEndToEndTest {

	/** anvil 기본 지갑 1번 (받는 쪽) */
	private static final String RECIPIENT = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";

	private static final BigInteger AMOUNT = new BigInteger("25500000000000000000");

	/** 이미지 entrypoint가 /bin/sh -c 라 명령 전체를 한 문자열로 넘긴다 */
	@Container
	static final GenericContainer<?> ANVIL = new GenericContainer<>("ghcr.io/foundry-rs/foundry:v1.8.7")
		.withCommand(new String[] { "anvil --host 0.0.0.0 --port 8545 --chain-id 31337" })
		.withExposedPorts(8545)
		.waitingFor(Wait.forLogMessage(".*Listening on.*", 1));

	@DynamicPropertySource
	static void anvilRpc(DynamicPropertyRegistry registry) {
		registry.add("chain.rpc-url", () -> "http://" + ANVIL.getHost() + ":" + ANVIL.getMappedPort(8545));
	}

	@Autowired
	private CreatePayoutUseCase createPayout;

	@Autowired
	private DispatchPayoutUseCase dispatchPayout;

	@Autowired
	private RunReconciliationUseCase reconciliation;

	@Autowired
	private ChainQueryUseCase chainQuery;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void cleanTables() {
		jdbc.update("DELETE FROM outbox_messages");
		jdbc.update("DELETE FROM processed_messages");
		jdbc.update("DELETE FROM reconciliation_findings");
		jdbc.update("DELETE FROM chain_transactions");
		jdbc.update("DELETE FROM payout_dispatches");
		jdbc.update("DELETE FROM payout_status_history");
		jdbc.update("DELETE FROM payouts");
	}

	@Test
	void payoutIsSentOnChainSettledAndAnnouncedToTheMerchant() throws Exception {
		BigInteger before = balance(RECIPIENT);

		UUID payoutId = createPayout.create(new CreatePayoutCommand("merchant_demo_01", RECIPIENT, AMOUNT, "e2e-001"))
			.payout()
			.id();

		awaitStatus(payoutId, "SETTLED", Duration.ofSeconds(60));

		// 체인: 받는 지갑에 정확히 지급 금액이 들어왔다
		assertThat(balance(RECIPIENT).subtract(before)).isEqualTo(AMOUNT);
		String txHash = jdbc.queryForObject("SELECT transaction_hash FROM chain_transactions WHERE payout_id = ?",
				String.class, payoutId);
		assertThat(chainQuery.getReceipt(txHash)).hasValueSatisfying(receipt -> assertThat(receipt.success()).isTrue());
		assertThat(jdbc.queryForMap("""
				SELECT status, included_block_number IS NOT NULL AS included, confirmations >= 1 AS confirmed
				FROM chain_transactions WHERE payout_id = ?""", payoutId))
			.containsEntry("status", "CONFIRMED")
			.containsEntry("included", true)
			.containsEntry("confirmed", true);
		assertThat(jdbc.queryForList("""
				SELECT to_status FROM payout_status_history WHERE payout_id = ? ORDER BY occurred_at, id""",
				String.class, payoutId))
			.containsExactly("REQUESTED", "AWAITING_CONFIRMATION", "SETTLED");

		// 가맹점 알림 이벤트도 Kafka로 발행된다
		awaitOutboxPublished(payoutId, "MERCHANT_PAYOUT_SETTLED", Duration.ofSeconds(30));

		// 같은 지급을 다시 처리해도(이벤트 중복 등) 두 번 보내지 않는다
		dispatchPayout.dispatch(payoutId);
		assertThat(balance(RECIPIENT).subtract(before)).isEqualTo(AMOUNT);

		// 실제 블록을 읽어 대사해도 장부와 어긋나는 것이 없다
		long block = jdbc.queryForObject("SELECT included_block_number FROM chain_transactions WHERE payout_id = ?",
				Long.class, payoutId);
		assertThat(reconciliation.run(block, block)).isZero();
	}

	private BigInteger balance(String address) {
		return chainQuery.getSigners()
			.stream()
			.filter(account -> account.address().equalsIgnoreCase(address))
			.map(account -> new BigInteger(account.balanceWei()))
			.findFirst()
			.orElseThrow();
	}

	private void awaitStatus(UUID payoutId, String status, Duration timeout) throws InterruptedException {
		long deadline = System.nanoTime() + timeout.toNanos();
		String current = null;
		while (System.nanoTime() < deadline) {
			current = jdbc.queryForObject("SELECT status FROM payouts WHERE id = ?", String.class, payoutId);
			if (status.equals(current)) {
				return;
			}
			Thread.sleep(200);
		}
		throw new AssertionError("payout " + payoutId + " is " + current + ", expected " + status);
	}

	private void awaitOutboxPublished(UUID payoutId, String type, Duration timeout) throws InterruptedException {
		long deadline = System.nanoTime() + timeout.toNanos();
		List<String> statuses = List.of();
		while (System.nanoTime() < deadline) {
			statuses = jdbc.queryForList("SELECT status FROM outbox_messages WHERE aggregate_id = ? AND type = ?",
					String.class, payoutId, type);
			if (statuses.equals(List.of("PUBLISHED"))) {
				return;
			}
			Thread.sleep(200);
		}
		throw new AssertionError(type + " outbox for " + payoutId + " is " + statuses);
	}

}
