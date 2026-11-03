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

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stablecoin.chain.domain.Eth;
import com.stablecoin.payout.application.port.in.CreatePayoutUseCase;
import com.stablecoin.payout.application.port.in.CreatePayoutUseCase.CreatePayoutCommand;
import com.stablecoin.payout.application.port.in.CreatePayoutUseCase.CreatePayoutResult;
import com.stablecoin.payout.application.port.in.GetPayoutUseCase;

@Tag(name = "Payout", description = "가맹점 지급")
@RestController
@RequestMapping("/api/payouts")
public class PayoutController {

	/** 같은 Idempotency-Key의 기존 지급을 돌려줬음을 알리는 응답 헤더 */
	static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

	private final CreatePayoutUseCase createPayout;

	private final GetPayoutUseCase getPayout;

	public PayoutController(CreatePayoutUseCase createPayout, GetPayoutUseCase getPayout) {
		this.createPayout = createPayout;
		this.getPayout = getPayout;
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

	@Operation(summary = "지급 조회",
			description = "지급 상태와 체인 전송 기록(전송 상태, 보낸 지갑, nonce, 트랜잭션 해시, 마지막 오류)을 함께 반환합니다. "
					+ "DB만 읽으므로 노드가 꺼져 있어도 조회할 수 있습니다. 소비자가 아직 처리하지 않았으면 dispatch는 null입니다.")
	@ApiResponse(responseCode = "200", description = "지급")
	@ApiResponse(responseCode = "400", description = "지급 ID가 UUID가 아님",
			content = @Content(mediaType = "application/problem+json",
					schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "없는 지급 (PAYOUT_NOT_FOUND)",
			content = @Content(mediaType = "application/problem+json",
					schema = @Schema(implementation = ProblemDetail.class)))
	@GetMapping("/{payoutId}")
	public PayoutDetailResponse get(@Parameter(description = "지급 ID") @PathVariable UUID payoutId) {
		return PayoutDetailResponse.of(getPayout.get(payoutId));
	}

}
