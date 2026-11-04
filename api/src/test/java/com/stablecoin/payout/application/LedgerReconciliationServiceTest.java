package com.stablecoin.payout.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

import java.math.BigInteger;
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
import com.stablecoin.chain.domain.ChainTransaction;
import com.stablecoin.chain.domain.SignedTransaction;
import com.stablecoin.payout.application.port.in.CreatePayoutUseCase;
import com.stablecoin.payout.application.port.in.CreatePayoutUseCase.CreatePayoutCommand;
import com.stablecoin.payout.application.port.out.PayoutChainPort;
import com.stablecoin.payout.application.port.out.ReconciliationJobPort;

/**
 * 실제 Postgres에서 대사가 장부를 고치고 불일치를 남기는지 확인한다. 체인은 가짜(PayoutChainPort)로 바꿔 끼운다.
 */
@SpringBootTest(properties = { "chain.rpc-url=http://127.0.0.1:1", "outbox.publisher.enabled=false",
		"payout.dispatcher.enabled=false", "payout.reconciliation.max-blocks=10" })
@Import(TestcontainersConfiguration.class)
class LedgerReconciliationServiceTest {

	private static final String WALLET = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";

	private static final String RECIPIENT = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";

	private static final BigInteger AMOUNT = new BigInteger("25500000000000000000");

	private static final long BLOCK = 7;

	@MockitoBean
	private PayoutChainPort chain;

	@MockitoBean
	private ReconciliationJobPort jobs;

	@Autowired
	private LedgerReconciliationService reconciliation;

	@Autowired
	private DispatchPayoutService dispatch;

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
		jdbc.update("DELETE FROM reconciliation_findings");
		jdbc.update("DELETE FROM chain_transactions");
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
		given(chain.latestBlockNumber()).willReturn(BLOCK);
		given(chain.blockTransactions(anyLong())).willReturn(List.of());
	}

	@Test
	void waitingPayoutWhoseTransferIsConfirmedIsSettledAndResolved() {
		UUID payoutId = dispatchedPayout("lag-1");
		onChain(transfer("0xhash-0", WALLET, RECIPIENT, AMOUNT), true);

		assertThat(reconciliation.run(BLOCK, BLOCK)).isEqualTo(1);

		assertThat(payoutStatus(payoutId)).isEqualTo("SETTLED");
		assertThat(chainRow(payoutId)).containsEntry("status", "CONFIRMED")
			.containsEntry("included_block_number", BLOCK)
			.containsEntry("confirmations", 1L);
		assertThat(finding("STATUS_LAG")).containsEntry("status", "RESOLVED")
			.containsEntry("payout_id", payoutId)
			.containsEntry("resolved", true);
		assertThat(merchantEvents(payoutId)).containsExactly("MERCHANT_PAYOUT_SETTLED");

		// 다시 실행해도 더 바뀌지 않는다
		assertThat(reconciliation.run(BLOCK, BLOCK)).isZero();
		assertThat(merchantEvents(payoutId)).containsExactly("MERCHANT_PAYOUT_SETTLED");
	}

	@Test
	void payoutThatNeverLearnedItWasSentIsSettled() {
		UUID payoutId = createdPayout("lag-unknown");
		doThrow(new ChainUnavailableException(new ResourceAccessException("timeout"))).when(chain).broadcast(any());
		try {
			dispatch.dispatch(payoutId);
		}
		catch (ChainUnavailableException expected) {
			// 노드는 받았지만 응답을 받지 못했다
		}
		assertThat(chainRow(payoutId)).containsEntry("status", "BROADCAST_UNKNOWN");
		onChain(transfer("0xhash-0", WALLET, RECIPIENT, AMOUNT), true);

		reconciliation.run(BLOCK, BLOCK);

		assertThat(payoutStatus(payoutId)).isEqualTo("SETTLED");
		assertThat(chainRow(payoutId)).containsEntry("status", "CONFIRMED");
	}

	@Test
	void transferNotInTheLedgerStaysOpenOnce() {
		onChain(transfer("0xdirect", WALLET, RECIPIENT, BigInteger.ONE), true);

		reconciliation.run(BLOCK, BLOCK);
		reconciliation.run(BLOCK, BLOCK);

		assertThat(finding("CHAIN_EVENT_WITHOUT_LEDGER")).containsEntry("status", "OPEN")
			.containsEntry("transaction_hash", "0xdirect")
			.containsEntry("log_index", 0);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM reconciliation_findings", Long.class)).isEqualTo(1);
	}

	@Test
	void transfersFromOtherWalletsAndRevertedTransfersAreIgnored() {
		given(chain.blockTransactions(BLOCK)).willReturn(List.of(transfer("0xother", RECIPIENT, WALLET, AMOUNT),
				transfer("0xreverted", WALLET.toUpperCase().replace("0X", "0x"), RECIPIENT, AMOUNT)));
		given(chain.receipt("0xreverted")).willReturn(Optional.of(receipt(false)));

		assertThat(reconciliation.run(BLOCK, BLOCK)).isZero();

		then(chain).should(never()).receipt("0xother");
	}

	@Test
	void payoutUnderReviewIsNotSettledByReconciliation() {
		UUID payoutId = createdPayout("review");
		doThrow(new ChainException("rejected")).when(chain).broadcast(any());
		dispatch.dispatch(payoutId);
		onChain(transfer("0xhash-0", WALLET, RECIPIENT, AMOUNT), true);

		reconciliation.run(BLOCK, BLOCK);

		assertThat(payoutStatus(payoutId)).isEqualTo("REQUIRES_REVIEW");
		assertThat(finding("BROADCAST_REJECTED")).containsEntry("status", "OPEN");
		assertThat(finding("STATUS_MISMATCH")).containsEntry("status", "OPEN");
		assertThat(merchantEvents(payoutId)).isEmpty();
	}

	@Test
	void rangeBeyondTheChainHeadIsNotAvailableYet() {
		assertThatThrownBy(() -> reconciliation.run(BLOCK, BLOCK + 1)).isInstanceOf(RangeNotAvailableException.class);
	}

	@Test
	void validRangeIsQueued() {
		reconciliation.enqueue(1, 10);

		then(jobs).should().enqueue(1, 10);
	}

	@Test
	void invertedOrTooLargeRangesAreRefused() {
		assertThatThrownBy(() -> reconciliation.enqueue(5, 4)).isInstanceOf(InvalidBlockRangeException.class);
		assertThatThrownBy(() -> reconciliation.enqueue(-1, 4)).isInstanceOf(InvalidBlockRangeException.class);
		assertThatThrownBy(() -> reconciliation.enqueue(1, 11)).isInstanceOf(InvalidBlockRangeException.class);

		then(jobs).should(never()).enqueue(anyLong(), anyLong());
	}

	private void onChain(ChainTransaction tx, boolean success) {
		given(chain.blockTransactions(BLOCK)).willReturn(List.of(tx));
		given(chain.receipt(tx.hash())).willReturn(Optional.of(receipt(success)));
	}

	private static ChainTransaction transfer(String hash, String from, String to, BigInteger value) {
		return new ChainTransaction(hash, from, to, value, 0, 21000, null, BLOCK, "0x");
	}

	private static ChainReceipt receipt(boolean success) {
		return new ChainReceipt(BLOCK, success, BigInteger.valueOf(21000), BigInteger.ONE);
	}

	private UUID dispatchedPayout(String key) {
		UUID payoutId = createdPayout(key);
		dispatch.dispatch(payoutId);
		return payoutId;
	}

	private UUID createdPayout(String key) {
		return createPayout.create(new CreatePayoutCommand("merchant_demo_01", RECIPIENT, AMOUNT, key)).payout().id();
	}

	private String payoutStatus(UUID payoutId) {
		return jdbc.queryForObject("SELECT status FROM payouts WHERE id = ?", String.class, payoutId);
	}

	private Map<String, Object> chainRow(UUID payoutId) {
		return jdbc.queryForMap("""
				SELECT status, included_block_number, confirmations FROM chain_transactions WHERE payout_id = ?""",
				payoutId);
	}

	private Map<String, Object> finding(String kind) {
		return jdbc.queryForMap("""
				SELECT status, payout_id, transaction_hash, log_index, resolved_at IS NOT NULL AS resolved
				FROM reconciliation_findings WHERE kind = ?""", kind);
	}

	private List<String> merchantEvents(UUID payoutId) {
		return jdbc.queryForList("""
				SELECT type FROM outbox_messages WHERE aggregate_id = ? AND type LIKE 'MERCHANT%'""", String.class,
				payoutId);
	}

}
