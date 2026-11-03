package com.stablecoin.payout.adapter.out.chain;

import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.stablecoin.chain.application.WalletSendLocks;
import com.stablecoin.chain.application.port.in.ChainQueryUseCase;
import com.stablecoin.chain.application.port.in.SendTransactionUseCase;
import com.stablecoin.chain.domain.ChainReceipt;
import com.stablecoin.chain.domain.ChainTransaction;
import com.stablecoin.chain.domain.SignedTransaction;
import com.stablecoin.payout.application.port.out.PayoutChainPort;

/**
 * 지급 지갑(chain.payout-wallet)으로 chain 모듈의 서명, 전송, 조회를 쓰는 {@link PayoutChainPort} 구현.
 * 지급 지갑의 키를 앱이 갖고 있지 않으면 앱 시작을 막는다.
 */
@Component
class ChainPayoutAdapter implements PayoutChainPort {

	private final SendTransactionUseCase sendTransaction;

	private final ChainQueryUseCase chainQuery;

	private final WalletSendLocks walletLocks;

	private final String payoutWallet;

	ChainPayoutAdapter(SendTransactionUseCase sendTransaction, ChainQueryUseCase chainQuery,
			WalletSendLocks walletLocks, @Value("${chain.payout-wallet}") String payoutWallet) {
		if (!chainQuery.canSign(payoutWallet)) {
			throw new IllegalStateException("chain.payout-wallet " + payoutWallet
					+ " is not a wallet the app can sign for (see GET /api/chain/signers)");
		}
		this.sendTransaction = sendTransaction;
		this.chainQuery = chainQuery;
		this.walletLocks = walletLocks;
		this.payoutWallet = payoutWallet;
	}

	@Override
	public String payoutWallet() {
		return payoutWallet;
	}

	@Override
	public <T> T withPayoutWalletLock(Supplier<T> action) {
		return walletLocks.withLock(payoutWallet, action);
	}

	@Override
	public SignedTransaction sign(String recipient, BigInteger wei, long minNonce) {
		return sendTransaction.sign(payoutWallet, recipient, wei, minNonce);
	}

	@Override
	public void broadcast(SignedTransaction transaction) {
		sendTransaction.broadcast(transaction);
	}

	@Override
	public Optional<ChainReceipt> receipt(String txHash) {
		return chainQuery.getReceipt(txHash);
	}

	@Override
	public Optional<ChainTransaction> transaction(String txHash) {
		return chainQuery.findTransaction(txHash);
	}

	@Override
	public long latestBlockNumber() {
		return chainQuery.getLatestBlockNumber();
	}

	@Override
	public long confirmedNonce(String address) {
		return chainQuery.getConfirmedNonce(address);
	}

}
