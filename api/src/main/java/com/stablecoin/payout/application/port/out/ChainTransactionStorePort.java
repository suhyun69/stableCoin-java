package com.stablecoin.payout.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.stablecoin.chain.domain.SignedTransaction;
import com.stablecoin.payout.domain.PayoutChainTransaction;

/**
 * 지급의 체인 트랜잭션(chain_transactions) 출력 포트. 호출한 쪽의 트랜잭션이 있으면 참여하고, 없으면 바로 커밋한다.
 */
public interface ChainTransactionStorePort {

	Optional<PayoutChainTransaction> findByPayoutId(UUID payoutId);

	/**
	 * 서명한 트랜잭션을 SIGNED 로 저장한다.
	 * @throws org.springframework.dao.DuplicateKeyException 같은 지갑의 nonce 가 이미 기록된 경우
	 */
	void saveSigned(UUID payoutId, SignedTransaction transaction);

	/**
	 * 이 지갑으로 서명하는 동안 다른 앱 인스턴스가 같은 지갑으로 서명하지 못하게 DB 잠금을 건다.
	 * 호출한 트랜잭션이 끝나면(방금 저장한 SIGNED 가 다른 인스턴스에 보일 때) 풀린다.
	 */
	void lockWalletForSigning(String senderAddress);

	/**
	 * 노드에 아직 없을 수 있는(SIGNED, BROADCAST_UNKNOWN) 트랜잭션이 쓰고 있는 nonce 다음 값. 없으면 0.
	 * 새 트랜잭션이 같은 nonce를 쓰지 않게 한다.
	 */
	long nextNonceFloor(String senderAddress);

	/**
	 * SIGNED, BROADCAST_UNKNOWN → PENDING. 노드가 트랜잭션을 받았다.
	 */
	void markPending(UUID payoutId);

	/**
	 * SIGNED → BROADCAST_UNKNOWN. 노드가 받았는지 알 수 없다.
	 */
	void markBroadcastUnknown(UUID payoutId, String error);

	/**
	 * 블록에 포함된 위치를 기록한다. success 면 MINED_SUCCESS 로 바꾼다. revert 면 확정될 때까지 상태를 그대로 둔다.
	 */
	void recordIncluded(UUID payoutId, long blockNumber, long confirmations, boolean success);

	/**
	 * PENDING 으로 되돌리고 블록 기록을 지운다. 블록 재구성으로 영수증이 사라졌다.
	 */
	void markNotIncluded(UUID payoutId);

	void markConfirmed(UUID payoutId);

	void markReverted(UUID payoutId);

	/**
	 * 이 트랜잭션은 블록에 들어가지 못한다.
	 */
	void markFailed(UUID payoutId, String error);

	void recordError(UUID payoutId, String error);

	/**
	 * 영수증을 기다리는 트랜잭션 (지급이 AWAITING_CONFIRMATION 이고 PENDING, MINED_SUCCESS).
	 */
	List<PayoutChainTransaction> findAwaitingConfirmation(int limit);

}
