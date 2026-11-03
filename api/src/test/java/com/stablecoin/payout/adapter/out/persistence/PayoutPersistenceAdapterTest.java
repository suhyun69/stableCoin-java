package com.stablecoin.payout.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import com.stablecoin.TestcontainersConfiguration;
import com.stablecoin.payout.domain.Payout;

/**
 * 실제 Postgres(Testcontainers)에 Flyway 스키마를 적용하고 매핑과 제약을 확인한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, PayoutPersistenceAdapter.class })
class PayoutPersistenceAdapterTest {

	private static final String RECIPIENT = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";

	@Autowired
	private PayoutPersistenceAdapter adapter;

	@Autowired
	private PayoutJpaRepository repository;

	@Test
	void savesAndFindsByIdempotencyKey() {
		Payout payout = Payout.requested("merchant_demo_01", RECIPIENT, new BigInteger("25500000000000000000"),
				"payout-2026-001");

		adapter.save(payout);

		assertThat(adapter.findByIdempotencyKey("payout-2026-001")).contains(payout);
		assertThat(adapter.findByIdempotencyKey("unknown")).isEmpty();
	}

	@Test
	void amountColumnHoldsUint256Max() {
		BigInteger uint256Max = BigInteger.TWO.pow(256).subtract(BigInteger.ONE);
		Payout payout = Payout.requested("merchant_demo_01", RECIPIENT, uint256Max, "big");

		adapter.save(payout);

		assertThat(repository.findById(payout.id()).orElseThrow().toDomain().amountWei()).isEqualTo(uint256Max);
	}

	@Test
	void sameIdempotencyKeyIsRejectedByUniqueConstraint() {
		adapter.save(Payout.requested("merchant_demo_01", RECIPIENT, BigInteger.ONE, "payout-2026-001"));

		assertThatThrownBy(
				() -> adapter.save(Payout.requested("merchant_demo_01", RECIPIENT, BigInteger.ONE, "payout-2026-001")))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void nonPositiveAmountIsRejectedByCheckConstraint() {
		assertThatThrownBy(
				() -> adapter.save(Payout.requested("merchant_demo_01", RECIPIENT, BigInteger.ZERO, "zero")))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
