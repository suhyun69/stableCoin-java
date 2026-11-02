package com.stablecoin.payout.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.stablecoin.chain.domain.Eth;
import com.stablecoin.payout.application.port.in.CreatePayoutUseCase;
import com.stablecoin.payout.application.port.in.CreatePayoutUseCase.CreatePayoutCommand;

@Tag(name = "Payout", description = "가맹점 지급")
@RestController
@RequestMapping("/api/payouts")
public class PayoutController {

	private final CreatePayoutUseCase createPayout;

	public PayoutController(CreatePayoutUseCase createPayout) {
		this.createPayout = createPayout;
	}

	@Operation(summary = "지급 요청",
			description = "지급을 저장하고 지급 지갑(chain.payout-wallet)에서 받는 지갑으로 바로 전송합니다. "
					+ "Idempotency-Key는 저장만 하고 아직 중복을 막지 않습니다.")
	@ApiResponse(responseCode = "202", description = "지급 요청 접수")
	@ApiResponse(responseCode = "400", description = "요청 값이 잘못됨 또는 Idempotency-Key 헤더 없음",
			content = @Content(mediaType = "application/problem+json",
					schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "503", description = "노드에 연결할 수 없음 (지급은 이미 저장된 상태일 수 있음)",
			content = @Content(mediaType = "application/problem+json",
					schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public PayoutResponse create(
			@Parameter(description = "요청마다 고유한 키. 재시도할 때는 같은 키를 보낸다.", example = "payout-2026-001")
			@RequestHeader("Idempotency-Key")
			@NotBlank(message = "비어 있을 수 없습니다")
			@Size(max = 255, message = "255자 이하여야 합니다") String idempotencyKey,
			@Valid @RequestBody CreatePayoutRequest request) {
		return PayoutResponse.of(createPayout.create(new CreatePayoutCommand(request.merchantId(),
				request.recipientAddress(), Eth.toWei(request.amountEth()), idempotencyKey)));
	}

}
