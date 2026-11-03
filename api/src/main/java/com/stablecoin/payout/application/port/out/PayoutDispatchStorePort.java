package com.stablecoin.payout.application.port.out;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.stablecoin.chain.domain.SignedTransaction;
import com.stablecoin.payout.domain.DispatchStatus;
import com.stablecoin.payout.domain.PayoutDispatch;

/**
 * 지급 전송 기록(payout_dispatches) 출력 포트. 호출한 쪽의 트랜잭션이 있으면 참여하고, 없으면 바로 커밋한다.
 */
public interface PayoutDispatchStorePort {

	/**
	 * 이 지급의 전송 기록을 STARTED 로 만든다. 이미 있으면 아무것도 하지 않는다 (지급마다 한 번만).
	 * @return 새로 만들었으면 true
	 */
	boolean startOnce(UUID payoutId);

	Optional<PayoutDispatch> find(UUID payoutId);

	/**
	 * 트랜잭션 안에서 행을 잠그고 읽는다. 같은 지급을 동시에 서명하지 않게 한다.
	 */
	Optional<PayoutDispatch> findForUpdate(UUID payoutId);

	/**
	 * STARTED → SIGNED. 서명한 트랜잭션을 저장한다.
	 */
	void markSigned(UUID payoutId, SignedTransaction transaction);

	/**
	 * expected 상태일 때만 next 로 바꾼다.
	 * @return 바꿨으면 true
	 */
	boolean transition(UUID payoutId, DispatchStatus expected, DispatchStatus next, String error);

	/**
	 * 이 지갑으로 서명하는 동안 다른 앱 인스턴스가 같은 지갑으로 서명하지 못하게 DB 잠금을 건다.
	 * 호출한 트랜잭션이 끝나면(방금 저장한 SIGNED 가 다른 인스턴스에 보일 때) 풀린다.
	 */
	void lockWalletForSigning(String fromAddress);

	/**
	 * 서명만 하고 아직 노드에 보내지 못한(SIGNED) 트랜잭션이 쓰고 있는 nonce 다음 값. 없으면 0.
	 * 새 트랜잭션이 같은 nonce를 쓰지 않게 한다.
	 */
	long nextNonceFloor(String fromAddress);

	List<PayoutDispatch> findByStatus(DispatchStatus status, int limit);

	/**
	 * olderThan 보다 오래 STARTED 나 SIGNED 에 머문 전송 기록 (소비자가 중간에 멈춘 경우).
	 */
	List<PayoutDispatch> findStuck(Duration olderThan, int limit);

}
