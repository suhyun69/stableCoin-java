package com.stablecoin.payout.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stablecoin.chain.domain.Eth;
import com.stablecoin.payout.application.port.in.CreatePayoutUseCase;
import com.stablecoin.payout.application.port.in.CreatePayoutUseCase.CreatePayoutCommand;
import com.stablecoin.payout.application.port.in.CreatePayoutUseCase.CreatePayoutResult;

@Tag(name = "Payout", description = "가맹점 지급")
@RestController
@RequestMapping("/api/payouts")
public class PayoutController {

	/** 같은 Idempotency-Key의 기존 지급을 돌려줬음을 알리는 응답 헤더 */
	static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

	private final CreatePayoutUseCase createPayout;

	public PayoutController(CreatePayoutUseCase createPayout) {
		this.createPayout = createPayout;
	}

	@Operation(summary = "지급 요청",
			description = "지급(REQUESTED), 상태 이력, 체인 전송 요청(outbox PAYOUT_REQUESTED)을 한 트랜잭션으로 저장하고 202를 반환합니다. "
					+ "체인 전송은 이 요청에서 하지 않습니다. 같은 Idempotency-Key로 같은 내용을 다시 보내면 처음 지급을 그대로 돌려주고 "
					+ "(Idempotent-Replayed: true), 다른 내용이면 409를 반환합니다.")
	@ApiResponse(responseCode = "202", description = "지급 요청 접수 (재시도면 처음 지급)",
			headers = @Header(name = IDEMPOTENT_REPLAYED, description = "기존 지급을 돌려준 경우 true",
					schema = @Schema(type = "boolean")))
	@ApiResponse(responseCode = "400", description = "요청 값이 잘못됨 또는 Idempotency-Key 헤더 없음",
			content = @Content(mediaType = "application/problem+json",
					schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "같은 Idempotency-Key로 다른 내용을 요청함 (IDEMPOTENCY_KEY_CONFLICT)",
			content = @Content(mediaType = "application/problem+json",
					schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping
	public ResponseEntity<PayoutResponse> create(
			@Parameter(description = "요청마다 고유한 키. 재시도할 때는 같은 키를 보낸다.", example = "payout-2026-001")
			@RequestHeader("Idempotency-Key")
			@NotBlank(message = "비어 있을 수 없습니다")
			@Size(max = 255, message = "255자 이하여야 합니다") String idempotencyKey,
			@Valid @RequestBody CreatePayoutRequest request) {
		CreatePayoutResult result = createPayout.create(new CreatePayoutCommand(request.merchantId(),
				request.recipientAddress(), Eth.toWei(request.amountEth()), idempotencyKey));
		ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.ACCEPTED);
		if (result.replayed()) {
			response.header(IDEMPOTENT_REPLAYED, "true");
		}
		return response.body(PayoutResponse.of(result.payout()));
	}

}
