package com.stablecoin.payout.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.stablecoin.TestcontainersConfiguration;
import com.stablecoin.chain.domain.SignedTransaction;
import com.stablecoin.payout.application.port.out.ChainTransactionStorePort;

/**
 * 앱 인스턴스 두 개가 같은 지갑으로 동시에 서명하는 상황을 트랜잭션 두 개로 재현한다.
 * DB 잠금이 없으면 둘 다 아직 커밋되지 않은 서로의 SIGNED 를 못 보고 같은 nonce를 고른다.
 */
@SpringBootTest(properties = { "chain.rpc-url=http://127.0.0.1:1", "outbox.publisher.enabled=false",
		"payout.dispatcher.enabled=false", "payout.confirmation.interval=1h" })
@Import(TestcontainersConfiguration.class)
class WalletSigningLockTest {

	/** 다른 테스트의 전송 기록과 섞이지 않게 이 테스트에서만 쓰는 지갑 */
	private static final String WALLET = "0x3000000000000000000000000000000000000AbC";

	@Autowired
	private ChainTransactionStorePort chainTransactions;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transaction;

	@Test
	void secondInstanceWaitsAndPicksTheNextNonce() throws Exception {
		String wallet = WALLET;
		long base = chainTransactions.nextNonceFloor(wallet);
		UUID first = payout();
		UUID second = payout();
		CountDownLatch firstLocked = new CountDownLatch(1);

		CompletableFuture<Long> a = CompletableFuture.supplyAsync(() -> transaction.execute(status -> {
			chainTransactions.lockWalletForSigning(wallet);
			firstLocked.countDown();
			long nonce = chainTransactions.nextNonceFloor(wallet);
			sleep(500); // 서명하는 동안
			chainTransactions.saveSigned(first, signed(wallet, nonce));
			return nonce;
		}));
		assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();
		CompletableFuture<Long> b = CompletableFuture.supplyAsync(() -> transaction.execute(status -> {
			chainTransactions.lockWalletForSigning(wallet.toLowerCase()); // 대소문자가 달라도 같은 잠금
			long nonce = chainTransactions.nextNonceFloor(wallet);
			chainTransactions.saveSigned(second, signed(wallet, nonce));
			return nonce;
		}));

		assertThat(a.get(10, TimeUnit.SECONDS)).isEqualTo(base);
		assertThat(b.get(10, TimeUnit.SECONDS)).isEqualTo(base + 1);
	}

	@Test
	void otherWalletsAreNotBlocked() throws Exception {
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> transaction.executeWithoutResult(status -> {
			chainTransactions.lockWalletForSigning("0x1000000000000000000000000000000000000001");
			locked.countDown();
			await(release);
		}));
		assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

		CompletableFuture<Void> other = CompletableFuture.runAsync(() -> transaction.executeWithoutResult(
				status -> chainTransactions.lockWalletForSigning("0x2000000000000000000000000000000000000002")));

		other.get(5, TimeUnit.SECONDS);
		release.countDown();
		holder.get(5, TimeUnit.SECONDS);
	}

	private UUID payout() {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO payouts (id, merchant_id, recipient_address, amount_wei, idempotency_key, request_fingerprint,
				  status) VALUES (?, 'merchant_demo_01', '0x70997970c51812dc3a010c7d01b50e0d17dc79c8', 1, ?, 'f',
				  'REQUESTED')""", id, "lock-" + id);
		return id;
	}

	private static SignedTransaction signed(String wallet, long nonce) {
		return new SignedTransaction(wallet, "0x70997970c51812dc3a010c7d01b50e0d17dc79c8", BigInteger.ONE, nonce,
				"0x02raw-" + nonce, "0x" + UUID.randomUUID().toString().replace("-", ""));
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await(10, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

}
