package com.stablecoin.chain;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

@Tag(name = "Chain", description = "로컬 노드(anvil) 조회")
@RestController
@RequestMapping("/api/chain")
public class ChainController {

	private final ChainService chainService;

	public ChainController(ChainService chainService) {
		this.chainService = chainService;
	}

	/**
	 * anvil이 생성한 지갑 목록과 잔액.
	 */
	@Operation(summary = "지갑 목록 조회", description = "anvil이 생성한 지갑 목록과 잔액을 조회합니다.")
	@ApiResponse(responseCode = "200", description = "조회 성공")
	@ApiResponse(responseCode = "502", description = "노드에 연결할 수 없거나 노드가 오류를 반환함",
			content = @Content(mediaType = "application/problem+json",
					schema = @Schema(implementation = ProblemDetail.class)))
	@GetMapping("/accounts")
	public List<Account> getAccounts() {
		return chainService.getAccounts();
	}

	/**
	 * 노드에 연결할 수 없거나 노드가 오류를 반환한 경우.
	 */
	@ExceptionHandler({ ChainException.class, RestClientException.class })
	public ProblemDetail handleChainError(RuntimeException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
	}

}
