package com.stablecoin.payout.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;

import com.stablecoin.TestcontainersConfiguration;
import com.stablecoin.chain.application.ChainException;
import com.stablecoin.chain.application.ChainUnavailableException;
import com.stablecoin.chain.domain.ChainReceipt;
import com.stablecoin.chain.domain.SignedTransaction;
import com.stablecoin.payout.application.port.in.CreatePayoutUseCase;
import com.stablecoin.payout.application.port.in.CreatePayoutUseCase.CreatePayoutCommand;
import com.stablecoin.payout.application.port.out.PayoutChainPort;
import com.stablecoin.payout.application.port.out.PayoutDispatchStorePort;

/**
 * 실제 Postgres에서 지급 전송과 정산의 상태 변화를 확인한다. 체인은 가짜(PayoutChainPort)로 바꿔 끼운다.
 */
@SpringBootTest(properties = { "chain.rpc-url=http://127.0.0.1:1", "outbox.publisher.enabled=false",
		"payout.dispatcher.enabled=false", "payout.dispatcher.stuck-after=0s" })
@Import(TestcontainersConfiguration.class)
class DispatchAndSettlementTest {

	private static final String WALLET = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";

	private static final String RECIPIENT = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";

	private static final BigInteger AMOUNT = new BigInteger("25500000000000000000");

	@MockitoBean
	private PayoutChainPort chain;

	@Autowired
	private DispatchPayoutService dispatch;

	@Autowired
	private ConfirmPayoutsService confirm;

	@Autowired
	private PayoutSettlementService settlement;

	@Autowired
	private PayoutDispatchStorePort dispatches;

	@Autowired
	private CreatePayoutUseCase createPayout;

	@Autowired
	private JdbcTemplate jdbc;

	private final AtomicInteger nonces = new AtomicInteger();

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		jdbc.update("DELETE FROM outbox_messages");
		jdbc.update("DELETE FROM processed_messages");
		jdbc.update("DELETE FROM payout_dispatches");
		jdbc.update("DELETE FROM payout_status_history");
		jdbc.update("DELETE FROM payouts");
		given(chain.payoutWallet()).willReturn(WALLET);
		given(chain.withPayoutWalletLock(any())).willAnswer(call -> ((Supplier<Object>) call.getArgument(0)).get());
		given(chain.sign(any(), any(), anyLong())).willAnswer(call -> {
			long nonce = Math.max(nonces.getAndIncrement(), (long) call.getArgument(2));
			return new SignedTransaction(WALLET, call.getArgument(0), call.getArgument(1), nonce, "0x02raw-" + nonce,
					"0xhash-" + nonce);
		});
	}

	@Test
	void dispatchSignsOnceBroadcastsAndWaitsForConfirmation() {
		UUID payoutId = createPayout("dispatch-1");

		dispatch.dispatch(payoutId);
		dispatch.dispatch(payoutId);

		then(chain).should(times(1)).sign(any(), any(), anyLong());
		then(chain).should(times(1)).broadcast(any());
		assertThat(payout(payoutId)).containsEntry("status", "AWAITING_CONFIRMATION")
			.containsEntry("transaction_hash", "0xhash-0");
		assertThat(dispatchRow(payoutId)).containsEntry("status", "BROADCAST")
			.containsEntry("tx_hash", "0xhash-0")
			.containsEntry("signed_tx", "0x02raw-0")
			.containsEntry("nonce", 0L)
			.containsEntry("from_address", WALLET);
		assertThat(history(payoutId)).containsExactly("NULL->REQUESTED PAYOUT_CREATED",
				"REQUESTED->AWAITING_CONFIRMATION TRANSACTION_BROADCAST");
	}

	@Test
	void sameMessageIsProcessedOnce() {
		UUID payoutId = createPayout("inbox-1");

		dispatch.dispatch(payoutId, "out-1");
		dispatch.dispatch(payoutId, "out-1");

		then(chain).should(times(1)).sign(any(), any(), anyLong());
		assertThat(jdbc.queryForList("""
				SELECT consumer_name || ' ' || message_id FROM processed_messages WHERE payout_id = ?""", String.class,
				payoutId))
			.containsExactly("payout-requested out-1");
	}

	@Test
	void tenConcurrentDeliveriesOfTheSameMessageStartOneDispatch() throws Exception {
		UUID payoutId = createPayout("inbox-concurrent");
		List<java.util.concurrent.CompletableFuture<Void>> deliveries = new java.util.ArrayList<>();
		java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
		for (int i = 0; i < 10; i++) {
			deliveries.add(java.util.concurrent.CompletableFuture.runAsync(() -> {
				try {
					start.await();
				}
				catch (InterruptedException ex) {
					throw new IllegalStateException(ex);
				}
				dispatch.dispatch(payoutId, "out-concurrent");
			}));
		}
		start.countDown();
		for (java.util.concurrent.CompletableFuture<Void> delivery : deliveries) {
			delivery.get();
		}

		assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_messages WHERE payout_id = ?", Integer.class,
				payoutId))
			.isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM payout_dispatches WHERE payout_id = ?", Integer.class,
				payoutId))
			.isEqualTo(1);
		then(chain).should(times(1)).sign(any(), any(), anyLong());
		then(chain).should(times(1)).broadcast(any());
	}

	@Test
	void redeliveredMessageIsSkippedAndTheStuckDispatchIsResumed() {
		UUID payoutId = createPayout("inbox-redelivery");
		org.mockito.Mockito.doThrow(new ChainUnavailableException(new ResourceAccessException("Connection refused")))
			.doNothing()
			.when(chain)
			.broadcast(any());
		try {
			dispatch.dispatch(payoutId, "out-redelivery");
		}
		catch (ChainUnavailableException expected) {
			// 서명까지 하고 보내지 못함
		}

		// Kafka가 같은 메시지를 다시 줘도 건너뛴다
		dispatch.dispatch(payoutId, "out-redelivery");
		then(chain).should(times(1)).broadcast(any());
		assertThat(dispatchRow(payoutId)).containsEntry("status", "SIGNED");

		// 멈춘 전송은 재개 작업이 이어간다
		dispatch.resumeStuck();
		assertThat(dispatchRow(payoutId)).containsEntry("status", "BROADCAST");
		then(chain).should(times(1)).sign(any(), any(), anyLong());
	}

	@Test
	void crashBeforeBroadcastIsResumedWithTheSameSignedTransaction() {
		UUID payoutId = createPayout("dispatch-resume");
		org.mockito.Mockito.doThrow(new ChainUnavailableException(new ResourceAccessException("Connection refused")))
			.doNothing()
			.when(chain)
			.broadcast(any());

		try {
			dispatch.dispatch(payoutId);
		}
		catch (ChainUnavailableException expected) {
			// 노드가 꺼져 있었다. 서명한 트랜잭션은 이미 저장됐다.
		}
		assertThat(dispatchRow(payoutId)).containsEntry("status", "SIGNED").containsEntry("tx_hash", "0xhash-0");
		assertThat(payout(payoutId)).containsEntry("status", "REQUESTED");

		assertThat(dispatch.resumeStuck()).isEqualTo(1);

		// 다시 서명하지 않고 저장해 둔 같은 트랜잭션을 다시 보낸다
		then(chain).should(times(1)).sign(any(), any(), anyLong());
		then(chain).should(times(2)).broadcast(new SignedTransaction(WALLET, RECIPIENT, AMOUNT, 0, "0x02raw-0",
				"0xhash-0"));
		assertThat(dispatchRow(payoutId)).containsEntry("status", "BROADCAST");
		assertThat(payout(payoutId)).containsEntry("status", "AWAITING_CONFIRMATION");
	}

	@Test
	void newPayoutDoesNotReuseTheNonceOfASignedButUnsentTransaction() {
		UUID stuck = createPayout("nonce-stuck");
		org.mockito.Mockito.doThrow(new ChainUnavailableException(new ResourceAccessException("down")))
			.doNothing()
			.when(chain)
			.broadcast(any());
		try {
			dispatch.dispatch(stuck);
		}
		catch (ChainUnavailableException expected) {
			// nonce 0 으로 서명만 하고 못 보냄
		}
		nonces.set(0); // 노드는 아직 nonce 0 이 비어 있다고 알려준다

		UUID next = createPayout("nonce-next");
		dispatch.dispatch(next);

		assertThat(dispatchRow(next)).containsEntry("nonce", 1L);
	}

	@Test
	void rejectionWhileSigningFailsThePayoutAndNotifiesTheMerchant() {
		UUID payoutId = createPayout("dispatch-rejected");
		given(chain.sign(any(), any(), anyLong()))
			.willThrow(new ChainException("eth_estimateGas: Insufficient funds for gas * price + value"));

		dispatch.dispatch(payoutId);

		then(chain).should(never()).broadcast(any());
		assertThat(payout(payoutId)).containsEntry("status", "FAILED");
		assertThat(dispatchRow(payoutId)).containsEntry("status", "FAILED");
		assertThat((String) dispatchRow(payoutId).get("last_error")).contains("Insufficient funds");
		assertThat(history(payoutId)).last().isEqualTo("REQUESTED->FAILED TRANSFER_REJECTED");
		assertThat(outboxTypes(payoutId)).containsExactly("PAYOUT_REQUESTED", "MERCHANT_PAYOUT_FAILED");
	}

	@Test
	void rejectionWhileBroadcastingRequiresReview() {
		UUID payoutId = createPayout("dispatch-review");
		org.mockito.Mockito.doThrow(new ChainException("nonce too low")).when(chain).broadcast(any());

		dispatch.dispatch(payoutId);

		assertThat(payout(payoutId)).containsEntry("status", "REQUIRES_REVIEW");
		assertThat(dispatchRow(payoutId)).containsEntry("status", "REQUIRES_REVIEW");
		assertThat(history(payoutId)).last().isEqualTo("REQUESTED->REQUIRES_REVIEW BROADCAST_REJECTED");
		assertThat(outboxTypes(payoutId)).containsExactly("PAYOUT_REQUESTED");
	}

	@Test
	void successfulReceiptSettlesAndNotifiesTheMerchantOnce() {
		UUID payoutId = createPayout("settle-1");
		dispatch.dispatch(payoutId);
		given(chain.receipt("0xhash-0")).willReturn(Optional.of(receipt(true)));

		assertThat(confirm.confirmBroadcast()).isEqualTo(1);
		// 같은 정산을 한 번 더 받아도 그대로 둔다
		assertThat(settlement.settle(payoutId, "0xhash-0")).isFalse();
		assertThat(confirm.confirmBroadcast()).isZero();

		assertThat(payout(payoutId)).containsEntry("status", "SETTLED").containsEntry("transaction_hash", "0xhash-0");
		assertThat(dispatchRow(payoutId)).containsEntry("status", "CONFIRMED");
		assertThat(history(payoutId)).containsExactly("NULL->REQUESTED PAYOUT_CREATED",
				"REQUESTED->AWAITING_CONFIRMATION TRANSACTION_BROADCAST",
				"AWAITING_CONFIRMATION->SETTLED RECEIPT_CONFIRMED");
		assertThat(outboxTypes(payoutId)).containsExactly("PAYOUT_REQUESTED", "MERCHANT_PAYOUT_SETTLED");
		assertThat(jdbc.queryForMap("""
				SELECT payload->>'merchantId' AS merchant, payload->>'transactionHash' AS tx,
				       payload->>'amountWei' AS amount, payload->>'recipientAddress' AS recipient
				FROM outbox_messages WHERE type = 'MERCHANT_PAYOUT_SETTLED'"""))
			.containsEntry("merchant", "merchant_demo_01")
			.containsEntry("tx", "0xhash-0")
			.containsEntry("amount", AMOUNT.toString())
			.containsEntry("recipient", RECIPIENT);
	}

	@Test
	void settlementIsRefusedFromTheWrongState() {
		UUID payoutId = createPayout("settle-wrong-state");

		assertThat(settlement.settle(payoutId, "0xwhatever")).isFalse();

		assertThat(payout(payoutId)).containsEntry("status", "REQUESTED");
		assertThat(outboxTypes(payoutId)).containsExactly("PAYOUT_REQUESTED");
	}

	@Test
	void revertedTransactionFailsThePayout() {
		UUID payoutId = createPayout("revert-1");
		dispatch.dispatch(payoutId);
		given(chain.receipt("0xhash-0")).willReturn(Optional.of(receipt(false)));

		confirm.confirmBroadcast();

		assertThat(payout(payoutId)).containsEntry("status", "FAILED");
		assertThat(dispatchRow(payoutId)).containsEntry("status", "FAILED");
		assertThat(outboxTypes(payoutId)).containsExactly("PAYOUT_REQUESTED", "MERCHANT_PAYOUT_FAILED");
	}

	@Test
	void missingReceiptWaitsThenRequiresReview() {
		UUID payoutId = createPayout("no-receipt");
		dispatch.dispatch(payoutId);
		given(chain.receipt("0xhash-0")).willReturn(Optional.empty());

		assertThat(new ConfirmPayoutsService(dispatches, chain, settlement, Duration.ofMinutes(10)).confirmBroadcast())
			.isZero();
		assertThat(payout(payoutId)).containsEntry("status", "AWAITING_CONFIRMATION");

		assertThat(new ConfirmPayoutsService(dispatches, chain, settlement, Duration.ZERO).confirmBroadcast())
			.isEqualTo(1);
		assertThat(payout(payoutId)).containsEntry("status", "REQUIRES_REVIEW");
		assertThat(history(payoutId)).last().isEqualTo("AWAITING_CONFIRMATION->REQUIRES_REVIEW CONFIRMATION_TIMEOUT");
	}

	@Test
	void unknownOrAlreadyHandledPayoutsAreSkipped() {
		dispatch.dispatch(UUID.randomUUID());
		UUID payoutId = createPayout("already-failed");
		jdbc.update("UPDATE payouts SET status = 'FAILED' WHERE id = ?", payoutId);

		dispatch.dispatch(payoutId);

		then(chain).should(never()).sign(any(), any(), anyLong());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM payout_dispatches", Integer.class)).isZero();
	}

	private static ChainReceipt receipt(boolean success) {
		return new ChainReceipt(7L, success, BigInteger.valueOf(21000), BigInteger.valueOf(1_000_000_000L));
	}

	private UUID createPayout(String idempotencyKey) {
		return createPayout.create(new CreatePayoutCommand("merchant_demo_01", RECIPIENT, AMOUNT, idempotencyKey))
			.payout()
			.id();
	}

	private Map<String, Object> payout(UUID payoutId) {
		return jdbc.queryForMap("SELECT status, transaction_hash FROM payouts WHERE id = ?", payoutId);
	}

	private Map<String, Object> dispatchRow(UUID payoutId) {
		return jdbc.queryForMap("""
				SELECT status, from_address, nonce, tx_hash, signed_tx, last_error
				FROM payout_dispatches WHERE payout_id = ?""", payoutId);
	}

	private List<String> history(UUID payoutId) {
		return jdbc.queryForList("""
				SELECT coalesce(from_status, 'NULL') || '->' || to_status || ' ' || reason
				FROM payout_status_history WHERE payout_id = ? ORDER BY occurred_at, id""", String.class, payoutId);
	}

	private List<String> outboxTypes(UUID payoutId) {
		return jdbc.queryForList("SELECT type FROM outbox_messages WHERE aggregate_id = ? ORDER BY created_at, type",
				String.class, payoutId);
	}

}
