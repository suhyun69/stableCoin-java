package com.stablecoin.payout.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigInteger;
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

	private static Payout payout(UUID id) {
		return new Payout(id, "merchant_demo_01", RECIPIENT, new BigInteger("25500000000000000000"),
				"payout-2026-001", "fingerprint", PayoutStatus.REQUESTED);
	}

}
