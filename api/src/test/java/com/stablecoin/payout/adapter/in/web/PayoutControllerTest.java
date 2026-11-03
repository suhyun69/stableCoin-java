package com.stablecoin.payout.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.stablecoin.payout.application.port.in.CreatePayoutUseCase;
import com.stablecoin.payout.application.IdempotencyKeyConflictException;
import com.stablecoin.payout.application.PayoutNotFoundException;
import com.stablecoin.payout.application.port.in.GetPayoutUseCase;
import com.stablecoin.payout.application.port.in.GetPayoutUseCase.PayoutView;
import com.stablecoin.payout.domain.DispatchStatus;
import com.stablecoin.payout.domain.PayoutDispatch;
import com.stablecoin.payout.application.port.in.CreatePayoutUseCase.CreatePayoutCommand;
import com.stablecoin.payout.application.port.in.CreatePayoutUseCase.CreatePayoutResult;
import com.stablecoin.payout.domain.Payout;
import com.stablecoin.payout.domain.PayoutStatus;

@WebMvcTest(PayoutController.class)
class PayoutControllerTest {

	private static final String RECIPIENT = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";

	private static final String BODY = """
			{"merchantId":"merchant_demo_01","recipientAddress":"%s","amountEth":25.5}
			""".formatted(RECIPIENT);

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CreatePayoutUseCase createPayout;

	@MockitoBean
	private GetPayoutUseCase getPayout;

	@Test
	void acceptsPayoutRequest() throws Exception {
		UUID id = UUID.randomUUID();
		given(createPayout.create(any())).willReturn(new CreatePayoutResult(payout(id), false));

		mockMvc.perform(post("/api/payouts").header("Idempotency-Key", "payout-2026-001")
			.contentType(MediaType.APPLICATION_JSON)
			.content(BODY))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.id").value(id.toString()))
			.andExpect(jsonPath("$.merchantId").value("merchant_demo_01"))
			.andExpect(jsonPath("$.amountWei").value("25500000000000000000"))
			.andExpect(jsonPath("$.amountEth").value("25.5"))
			.andExpect(jsonPath("$.status").value("REQUESTED"))
			.andExpect(jsonPath("$.idempotencyKey").doesNotExist())
			.andExpect(jsonPath("$.requestFingerprint").doesNotExist())
			.andExpect(header().doesNotExist("Idempotent-Replayed"));

		ArgumentCaptor<CreatePayoutCommand> command = ArgumentCaptor.forClass(CreatePayoutCommand.class);
		then(createPayout).should().create(command.capture());
		org.assertj.core.api.Assertions.assertThat(command.getValue())
			.isEqualTo(new CreatePayoutCommand("merchant_demo_01", RECIPIENT, new BigInteger("25500000000000000000"),
					"payout-2026-001"));
	}

	@Test
	void replayedPayoutIsMarkedWithHeader() throws Exception {
		UUID id = UUID.randomUUID();
		given(createPayout.create(any())).willReturn(new CreatePayoutResult(payout(id), true));

		mockMvc.perform(post("/api/payouts").header("Idempotency-Key", "payout-2026-001")
			.contentType(MediaType.APPLICATION_JSON)
			.content(BODY))
			.andExpect(status().isAccepted())
			.andExpect(header().string("Idempotent-Replayed", "true"))
			.andExpect(jsonPath("$.id").value(id.toString()));
	}

	@Test
	void sameKeyWithDifferentBodyReturnsConflict() throws Exception {
		given(createPayout.create(any())).willThrow(new IdempotencyKeyConflictException("payout-2026-001"));

		mockMvc.perform(post("/api/payouts").header("Idempotency-Key", "payout-2026-001")
			.contentType(MediaType.APPLICATION_JSON)
			.content(BODY))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));
	}

	@Test
	void missingIdempotencyKeyReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/payouts").contentType(MediaType.APPLICATION_JSON).content(BODY))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.detail").value("Idempotency-Key: 필수 헤더입니다"));

		then(createPayout).should(never()).create(any());
	}

	@Test
	void blankIdempotencyKeyReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/payouts").header("Idempotency-Key", " ")
			.contentType(MediaType.APPLICATION_JSON)
			.content(BODY))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		then(createPayout).should(never()).create(any());
	}

	@ParameterizedTest
	@ValueSource(strings = { "{}", """
			{"merchantId":"","recipientAddress":"%s","amountEth":1}
			""", """
			{"merchantId":"m","recipientAddress":"nope","amountEth":1}
			""", """
			{"merchantId":"m","recipientAddress":"%s","amountEth":-1}
			""", """
			{"merchantId":"m","recipientAddress":"%s","amountEth":0.0000000000000000001}
			""" })
	void invalidBodyReturnsBadRequest(String body) throws Exception {
		mockMvc.perform(post("/api/payouts").header("Idempotency-Key", "k")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body.formatted(RECIPIENT)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		then(createPayout).should(never()).create(any());
	}

	@Test
	void returnsPayoutWithItsDispatchState() throws Exception {
		UUID id = UUID.randomUUID();
		PayoutDispatch dispatch = new PayoutDispatch(id, DispatchStatus.BROADCAST,
				"0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266", 12L, "0xabc", "0x02signed", null,
				Instant.parse("2026-11-04T01:02:03Z"));
		given(getPayout.get(id)).willReturn(new PayoutView(payout(id), dispatch));

		mockMvc.perform(get("/api/payouts/{id}", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id.toString()))
			.andExpect(jsonPath("$.status").value("REQUESTED"))
			.andExpect(jsonPath("$.amountEth").value("25.5"))
			.andExpect(jsonPath("$.dispatch.status").value("BROADCAST"))
			.andExpect(jsonPath("$.dispatch.fromAddress").value("0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266"))
			.andExpect(jsonPath("$.dispatch.nonce").value(12))
			.andExpect(jsonPath("$.dispatch.transactionHash").value("0xabc"))
			.andExpect(jsonPath("$.dispatch.updatedAt").value("2026-11-04T01:02:03Z"))
			// 서명한 트랜잭션 원본과 멱등성 키는 내보내지 않는다
			.andExpect(jsonPath("$.dispatch.signedTx").doesNotExist())
			.andExpect(jsonPath("$.idempotencyKey").doesNotExist());
	}

	@Test
	void returnsNullDispatchBeforeTheConsumerHandlesThePayout() throws Exception {
		UUID id = UUID.randomUUID();
		given(getPayout.get(id)).willReturn(new PayoutView(payout(id), null));

		mockMvc.perform(get("/api/payouts/{id}", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REQUESTED"))
			.andExpect(jsonPath("$.dispatch").value(org.hamcrest.Matchers.nullValue()));
	}

	@Test
	void unknownPayoutReturnsNotFound() throws Exception {
		UUID id = UUID.randomUUID();
		given(getPayout.get(id)).willThrow(new PayoutNotFoundException(id));

		mockMvc.perform(get("/api/payouts/{id}", id))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PAYOUT_NOT_FOUND"));
	}

	@Test
	void payoutIdThatIsNotAUuidReturnsBadRequest() throws Exception {
		mockMvc.perform(get("/api/payouts/{id}", "pay_001"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.detail").value("payoutId: 형식이 올바르지 않습니다"));

		then(getPayout).should(never()).get(any());
	}

	private static Payout payout(UUID id) {
		return new Payout(id, "merchant_demo_01", RECIPIENT, new BigInteger("25500000000000000000"),
				"payout-2026-001", "fingerprint", PayoutStatus.REQUESTED);
	}

}
