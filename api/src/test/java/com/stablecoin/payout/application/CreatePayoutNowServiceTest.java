package com.stablecoin.payout.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.web.client.ResourceAccessException;

import com.stablecoin.chain.application.ChainUnavailableException;
import com.stablecoin.payout.application.port.in.CreatePayoutUseCase.CreatePayoutCommand;
import com.stablecoin.payout.application.port.out.PayoutRepositoryPort;
import com.stablecoin.payout.application.port.out.PayoutTransferPort;
import com.stablecoin.payout.domain.Payout;
import com.stablecoin.payout.domain.PayoutStatus;

/**
 * CreatePayoutNow의 동작과, 일부러 남겨 둔 Dual Write·멱등성 문제를 현재 동작 그대로 기록한다.
 * 문제를 고치면 아래 "KNOWN ISSUE" 테스트의 기대값이 바뀌어야 한다.
 */
class CreatePayoutNowServiceTest {

	private static final String RECIPIENT = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";

	private static final CreatePayoutCommand COMMAND = new CreatePayoutCommand("merchant_demo_01", RECIPIENT,
			new BigInteger("25500000000000000000"), "payout-2026-001");

	/** 저장된 지급을 기록하는 가짜 저장소 */
	private final List<Payout> saved = new ArrayList<>();

	private final PayoutRepositoryPort payouts = mock(PayoutRepositoryPort.class);

	private final PayoutTransferPort payoutTransfer = mock(PayoutTransferPort.class);

	private final CreatePayoutNowService service = new CreatePayoutNowService(payouts, payoutTransfer);

	@BeforeEach
	void setUp() {
		given(payouts.save(any())).willAnswer(invocation -> {
			Payout payout = invocation.getArgument(0);
			saved.add(payout);
			return payout;
		});
		given(payoutTransfer.transfer(any())).willReturn("0xabc");
	}

	@Test
	void savesRequestedPayoutThenTransfers() {
		Payout payout = service.create(COMMAND);

		assertThat(payout.status()).isEqualTo(PayoutStatus.REQUESTED);
		assertThat(payout.merchantId()).isEqualTo("merchant_demo_01");
		assertThat(payout.recipientAddress()).isEqualTo(RECIPIENT);
		assertThat(payout.amountWei()).isEqualTo(new BigInteger("25500000000000000000"));
		assertThat(payout.idempotencyKey()).isEqualTo("payout-2026-001");
		InOrder order = inOrder(payouts, payoutTransfer);
		order.verify(payouts).save(any());
		order.verify(payoutTransfer).transfer(payout);
	}

	@Test
	void knownIssue_dualWrite_failedTransferLeavesSavedPayout() {
		given(payoutTransfer.transfer(any()))
			.willThrow(new ChainUnavailableException(new ResourceAccessException("Connection refused")));

		assertThatThrownBy(() -> service.create(COMMAND)).isInstanceOf(ChainUnavailableException.class);

		// DB에는 "요청됨"이 남았지만 돈은 나가지 않았다
		assertThat(saved).hasSize(1);
		assertThat(saved.get(0).status()).isEqualTo(PayoutStatus.REQUESTED);
	}

	@Test
	void knownIssue_noIdempotency_retryWithSameKeyPaysTwice() {
		service.create(COMMAND);
		service.create(COMMAND);

		// 같은 Idempotency-Key인데 지급이 두 번 저장되고 두 번 전송된다
		assertThat(saved).hasSize(2).extracting(Payout::idempotencyKey).containsOnly("payout-2026-001");
		assertThat(saved.get(0).id()).isNotEqualTo(saved.get(1).id());
		then(payoutTransfer).should(times(2)).transfer(any());
	}

}
