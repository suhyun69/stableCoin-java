package com.stablecoin.chain;

import java.util.List;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

@Tag(name = "Chain", description = "로컬 노드(anvil) 조회와 전송")
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
	@ApiResponse(responseCode = "502", description = "노드에 연결할 수 없음",
			content = @Content(mediaType = "application/problem+json",
					schema = @Schema(implementation = ProblemDetail.class)))
	@GetMapping("/accounts")
	public List<Account> getAccounts() {
		return chainService.getAccounts();
	}

	/**
	 * 지갑 간 ETH 전송.
	 */
	@Operation(summary = "ETH 전송", description = "노드가 서명할 수 있는 지갑(anvil 기본 지갑)에서 다른 지갑으로 ETH를 전송합니다.")
	@ApiResponse(responseCode = "200", description = "전송 완료")
	@ApiResponse(responseCode = "400", description = "요청 값이 잘못되었거나 노드가 전송을 거부함 (잔액 부족, 서명할 수 없는 지갑 등)",
			content = @Content(mediaType = "application/problem+json",
					schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "502", description = "노드에 연결할 수 없음",
			content = @Content(mediaType = "application/problem+json",
					schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping("/transfers")
	public Transfer transfer(@Valid @RequestBody TransferRequest request) {
		return chainService.transfer(request.from(), request.to(), Eth.toWei(request.amountEth()));
	}

	/**
	 * 요청 값 검증에 실패한 경우. 어떤 필드가 왜 잘못되었는지 detail에 담는다.
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleInvalidRequest(MethodArgumentNotValidException ex) {
		String detail = ex.getFieldErrors()
			.stream()
			.map(error -> error.getField() + ": " + error.getDefaultMessage())
			.sorted()
			.collect(Collectors.joining(", "));
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
	}

	/**
	 * 노드가 요청을 거부한 경우.
	 */
	@ExceptionHandler(ChainException.class)
	public ProblemDetail handleChainError(ChainException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	/**
	 * 노드에 연결할 수 없는 경우.
	 */
	@ExceptionHandler(RestClientException.class)
	public ProblemDetail handleNodeUnavailable(RestClientException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
	}

}
