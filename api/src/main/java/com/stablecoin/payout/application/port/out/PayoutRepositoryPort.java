package com.stablecoin.payout.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.stablecoin.payout.domain.Payout;
import com.stablecoin.payout.domain.PayoutStatus;

/**
 * 지급 저장소 출력 포트. 호출한 쪽의 DB 트랜잭션에 참여한다.
 */
public interface PayoutRepositoryPort {

	/**
	 * @throws org.springframework.dao.DataIntegrityViolationException 같은 멱등성 키가 이미 있는 경우
	 */
	Payout save(Payout payout);

	Optional<Payout> findByIdempotencyKey(String idempotencyKey);

	Optional<Payout> findById(UUID id);

	/**
	 * expected 상태일 때만 next 로 바꾼다. transactionHash 가 null 이 아니면 함께 저장한다.
	 * @return 바꿨으면 true
	 */
	boolean transition(UUID id, PayoutStatus expected, PayoutStatus next, String transactionHash);

	/**
	 * 저장된 체인 트랜잭션 해시. 없으면 비어 있다.
	 */
	Optional<String> findTransactionHash(UUID id);

}
