package com.stablecoin.payout.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.stablecoin.payout.application.port.in.RunReconciliationUseCase;

@Tag(name = "Reconciliation", description = "장부 대사 (내부용. 인증이 없으므로 외부에 노출하지 않는다)")
@RestController
@RequestMapping("/internal/reconciliation")
public class ReconciliationController {

	private final RunReconciliationUseCase reconciliation;

	public ReconciliationController(RunReconciliationUseCase reconciliation) {
		this.reconciliation = reconciliation;
	}

	@Operation(summary = "대사 실행 요청",
			description = "블록 범위에서 지급 지갑이 보낸 전송을 장부와 비교하는 작업을 대기열에 넣고 202를 반환합니다. "
					+ "결과는 reconciliation_findings 테이블에 남습니다.")
	@ApiResponse(responseCode = "202", description = "작업 접수")
	@ApiResponse(responseCode = "400", description = "범위가 잘못됨 (INVALID_BLOCK_RANGE)",
			content = @Content(mediaType = "application/problem+json",
					schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping("/runs")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public RunRequest run(@Valid @RequestBody RunRequest request) {
		reconciliation.enqueue(request.fromBlock(), request.toBlock());
		return request;
	}

	@Schema(description = "대사할 블록 범위 (양끝 포함)")
	public record RunRequest(
			@Schema(description = "시작 블록", example = "0") @NotNull(message = "필수 값입니다") Long fromBlock,
			@Schema(description = "끝 블록", example = "100") @NotNull(message = "필수 값입니다") Long toBlock) {
	}

}
