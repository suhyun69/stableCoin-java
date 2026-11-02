package com.stablecoin.payout.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import com.stablecoin.TestcontainersConfiguration;
import com.stablecoin.payout.domain.Payout;

/**
 * 실제 Postgres(Testcontainers)에 Flyway 스키마를 적용하고 매핑을 확인한다.
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
	void savesAndReadsBackPayout() {
		Payout payout = Payout.requested("merchant_demo_01", RECIPIENT, new BigInteger("25500000000000000000"),
				"payout-2026-001");

		adapter.save(payout);

		assertThat(repository.findById(payout.id())).get().extracting(PayoutEntity::toDomain).isEqualTo(payout);
	}

	@Test
	void amountColumnHoldsUint256Max() {
		BigInteger uint256Max = BigInteger.TWO.pow(256).subtract(BigInteger.ONE);
		Payout payout = Payout.requested("merchant_demo_01", RECIPIENT, uint256Max, "big");

		adapter.save(payout);
		repository.flush();

		assertThat(repository.findById(payout.id()).orElseThrow().toDomain().amountWei()).isEqualTo(uint256Max);
	}

	@Test
	void knownIssue_sameIdempotencyKeyIsStoredTwice() {
		adapter.save(Payout.requested("merchant_demo_01", RECIPIENT, BigInteger.ONE, "payout-2026-001"));
		adapter.save(Payout.requested("merchant_demo_01", RECIPIENT, BigInteger.ONE, "payout-2026-001"));
		repository.flush();

		// UNIQUE 제약이 없어 같은 키가 두 행으로 저장된다
		assertThat(repository.findByIdempotencyKey("payout-2026-001")).hasSize(2);
	}

}
