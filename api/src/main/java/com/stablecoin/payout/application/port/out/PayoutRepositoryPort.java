package com.stablecoin.payout.application.port.out;

import java.util.Optional;

import com.stablecoin.payout.domain.Payout;

/**
 * 지급 저장소 출력 포트. 호출한 쪽의 DB 트랜잭션에 참여한다.
 */
public interface PayoutRepositoryPort {

	/**
	 * @throws org.springframework.dao.DataIntegrityViolationException 같은 멱등성 키가 이미 있는 경우
	 */
	Payout save(Payout payout);

	Optional<Payout> findByIdempotencyKey(String idempotencyKey);

}
