package com.stablecoin.chain;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chain", description = "로컬 노드(anvil) 조회와 전송")
@RestController
@RequestMapping("/api/chain")
public class ChainController {

	private static final String TX_HASH = "^0x[0-9a-fA-F]{64}$";

	private final ChainService chainService;

	public ChainController(ChainService chainService) {
		this.chainService = chainService;
	}

	/**
	 * anvil이 생성한 지갑 목록과 잔액.
	 */
	@Operation(summary = "지갑 목록 조회", description = "anvil이 생성한 지갑 목록과 잔액을 조회합니다.")
	@ApiResponse(responseCode = "200", description = "조회 성공")
	@ApiResponse(responseCode = "503", description = "노드에 연결할 수 없음 (Retry-After 헤더로 재시도 시점 안내)",
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
	@ApiResponse(responseCode = "503", description = "노드에 연결할 수 없음 (Retry-After 헤더로 재시도 시점 안내)",
			content = @Content(mediaType = "application/problem+json",
					schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping("/transfers")
	public Transfer transfer(@Valid @RequestBody TransferRequest request) {
		return chainService.transfer(request.from(), request.to(), Eth.toWei(request.amountEth()));
	}

	/**
	 * 트랜잭션 상세 조회.
	 */
	@Operation(summary = "트랜잭션 상세 조회", description = "트랜잭션 해시로 상태, 블록, 금액, 가스, 수수료 등 상세 정보를 조회합니다.")
	@ApiResponse(responseCode = "200", description = "조회 성공")
	@ApiResponse(responseCode = "400", description = "트랜잭션 해시 형식이 잘못됨",
			content = @Content(mediaType = "application/problem+json",
					schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "노드에 해당 트랜잭션이 없음",
			content = @Content(mediaType = "application/problem+json",
					schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "503", description = "노드에 연결할 수 없음 (Retry-After 헤더로 재시도 시점 안내)",
			content = @Content(mediaType = "application/problem+json",
					schema = @Schema(implementation = ProblemDetail.class)))
	@GetMapping("/transactions/{txHash}")
	public TransactionDetail getTransaction(@Parameter(description = "트랜잭션 해시 (0x + 64자리 hex)",
			example = "0x236660fa85ffc77d11a69d8ccbbe440d4182516d9bf1bbef0af6baa271b31a15") @PathVariable @Pattern(
					regexp = TX_HASH, message = "올바른 트랜잭션 해시가 아닙니다") String txHash) {
		return chainService.getTransaction(txHash);
	}

}
