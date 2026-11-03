package com.stablecoin.payout.application.port.out;

import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Supplier;

import com.stablecoin.chain.domain.ChainReceipt;
import com.stablecoin.chain.domain.SignedTransaction;

/**
 * 지급 지갑(chain.payout-wallet)으로 체인을 다루는 출력 포트.
 * sign 부터 broadcast 까지는 withPayoutWalletLock 안에서 호출해야 nonce가 겹치지 않는다.
 */
public interface PayoutChainPort {

	String payoutWallet();

	<T> T withPayoutWalletLock(Supplier<T> action);

	/**
	 * 지급 지갑에서 recipient 로 wei 를 보내는 트랜잭션에 서명한다 (노드에 보내지 않음).
	 */
	SignedTransaction sign(String recipient, BigInteger wei, long minNonce);

	/**
	 * 서명한 트랜잭션을 보낸다. 같은 트랜잭션을 다시 보내도 안전하다.
	 */
	void broadcast(SignedTransaction transaction);

	Optional<ChainReceipt> receipt(String txHash);

}
