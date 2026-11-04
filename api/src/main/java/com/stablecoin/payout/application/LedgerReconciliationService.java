package com.stablecoin.payout.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.stablecoin.chain.domain.ChainReceipt;
import com.stablecoin.chain.domain.ChainTransaction;
import com.stablecoin.payout.application.port.in.RunReconciliationUseCase;
import com.stablecoin.payout.application.port.out.ChainTransactionStorePort;
import com.stablecoin.payout.application.port.out.PayoutChainPort;
import com.stablecoin.payout.application.port.out.ReconciliationJobPort;
import com.stablecoin.payout.application.port.out.ReconciliationStorePort;
import com.stablecoin.payout.domain.ChainPayment;
import com.stablecoin.payout.domain.LedgerEntry;
import com.stablecoin.payout.domain.ReconciliationClassifier;
import com.stablecoin.payout.domain.ReconciliationFinding;

/**
 * 블록 범위에서 지급 지갑이 보내 성공한 ETH 전송을 읽어 장부와 비교한다 (stableCoin TS RunLedgerReconciliation).
 * ETH 전송에는 Transfer 이벤트가 없어 블록의 트랜잭션을 직접 읽는다.
 * STATUS_LAG 는 바로 정산해 RESOLVED 로, 나머지 불일치는 사람이 보도록 OPEN 으로 남긴다.
 * 같은 범위를 다시 실행해도 안전하다: 같은 문제는 OPEN 인 동안 한 줄만 남고, 정산은 지급이 기대한 상태일 때만 일어난다.
 */
@Service
public class LedgerReconciliationService implements RunReconciliationUseCase {

	private static final Logger log = LoggerFactory.getLogger(LedgerReconciliationService.class);

	private final PayoutChainPort chain;

	private final ReconciliationStorePort store;

	private final ReconciliationJobPort jobs;

	private final ChainTransactionStorePort chainTransactions;

	private final PayoutSettlementService settlement;

	private final int requiredConfirmations;

	private final long maxBlocks;

	public LedgerReconciliationService(PayoutChainPort chain, ReconciliationStorePort store, ReconciliationJobPort jobs,
			ChainTransactionStorePort chainTransactions, PayoutSettlementService settlement,
			@Value("${payout.confirmation.required-confirmations:1}") int requiredConfirmations,
			@Value("${payout.reconciliation.max-blocks:1000}") long maxBlocks) {
		this.chain = chain;
		this.store = store;
		this.jobs = jobs;
		this.chainTransactions = chainTransactions;
		this.settlement = settlement;
		this.requiredConfirmations = requiredConfirmations;
		this.maxBlocks = maxBlocks;
	}

	@Override
	public void enqueue(long fromBlock, long toBlock) {
		if (fromBlock < 0 || fromBlock > toBlock) {
			throw new InvalidBlockRangeException("fromBlock must be between 0 and toBlock");
		}
		if (toBlock - fromBlock + 1 > maxBlocks) {
			throw new InvalidBlockRangeException("range must be at most " + maxBlocks + " blocks");
		}
		jobs.enqueue(fromBlock, toBlock);
	}

	@Override
	public int run(long fromBlock, long toBlock) {
		long head = chain.latestBlockNumber();
		if (toBlock > head) {
			throw new RangeNotAvailableException(head, toBlock);
		}
		List<ChainPayment> payments = readPayments(fromBlock, toBlock);
		List<LedgerEntry> ledger = store.readLedger(fromBlock, toBlock,
				payments.stream().map(ChainPayment::transactionHash).toList());
		List<ReconciliationFinding> findings = ReconciliationClassifier.classify(ledger, payments, head,
				requiredConfirmations);
		findings.forEach(this::apply);
		log.info("Reconciled blocks {}-{}: {} payments, {} ledger entries, {} findings", fromBlock, toBlock,
				payments.size(), ledger.size(), findings.size());
		return findings.size();
	}

	private List<ChainPayment> readPayments(long fromBlock, long toBlock) {
		String wallet = chain.payoutWallet();
		List<ChainPayment> payments = new ArrayList<>();
		for (long block = fromBlock; block <= toBlock; block++) {
			List<ChainTransaction> transactions = chain.blockTransactions(block);
			for (int index = 0; index < transactions.size(); index++) {
				ChainTransaction tx = transactions.get(index);
				if (wallet.equalsIgnoreCase(tx.from()) && succeeded(tx.hash())) {
					payments.add(new ChainPayment(tx.hash(), index, block, tx.from(), tx.to(), tx.value()));
				}
			}
		}
		return payments;
	}

	private boolean succeeded(String txHash) {
		Optional<ChainReceipt> receipt = chain.receipt(txHash);
		return receipt.isPresent() && receipt.get().success();
	}

	private void apply(ReconciliationFinding finding) {
		if (!ReconciliationFinding.STATUS_LAG.equals(finding.kind())) {
			log.warn("Reconciliation finding {} for payout {} transaction {}: {}", finding.kind(), finding.payoutId(),
					finding.transactionHash(), finding.reason());
			store.recordOpen(finding);
			return;
		}
		// 노드가 받았는지 몰랐던 지급이면 REQUESTED 라서 먼저 AWAITING_CONFIRMATION 으로 옮긴다
		settlement.markBroadcast(finding.payoutId());
		chainTransactions.recordIncluded(finding.payoutId(), finding.blockNumber(), finding.confirmations(), true);
		if (settlement.settle(finding.payoutId())) {
			log.info("Settled lagging payout {} from reconciliation", finding.payoutId());
			store.recordResolved(finding);
		}
	}

}
