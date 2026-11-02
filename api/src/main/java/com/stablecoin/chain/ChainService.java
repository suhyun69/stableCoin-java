package com.stablecoin.chain;

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * anvil 노드의 JSON-RPC를 호출해 체인 정보를 조회한다.
 */
@Service
public class ChainService {

	private static final Duration RECEIPT_POLL_INTERVAL = Duration.ofMillis(100);

	private final RestClient restClient;

	private final Duration receiptTimeout;

	private final AtomicLong requestId = new AtomicLong();

	public ChainService(RestClient.Builder builder, @Value("${chain.rpc-url}") String rpcUrl,
			@Value("${chain.receipt-timeout:2s}") Duration receiptTimeout) {
		this.restClient = builder.baseUrl(rpcUrl).build();
		this.receiptTimeout = receiptTimeout;
	}

	/**
	 * 노드의 체인 ID를 조회한다. 노드 연결 확인에도 쓴다.
	 */
	public long getChainId() {
		String hex = call("eth_chainId", List.of(), new ParameterizedTypeReference<JsonRpcResponse<String>>() {});
		return Eth.fromHexLong(hex);
	}

	/**
	 * 노드가 관리하는 지갑 목록과 잔액을 조회한다.
	 */
	public List<Account> getAccounts() {
		List<String> addresses = call("eth_accounts", List.of(),
				new ParameterizedTypeReference<JsonRpcResponse<List<String>>>() {});
		return addresses.stream()
			.map(address -> Account.of(address, getBalance(address)))
			.toList();
	}

	/**
	 * 지갑의 최신 블록 기준 잔액(wei)을 조회한다.
	 */
	public BigInteger getBalance(String address) {
		String hex = call("eth_getBalance", List.of(address, "latest"),
				new ParameterizedTypeReference<JsonRpcResponse<String>>() {});
		return Eth.fromHex(hex);
	}

	/**
	 * 노드가 서명할 수 있는 지갑(from)에서 다른 지갑(to)으로 ETH를 전송한다.
	 * anvil은 트랜잭션 해시를 먼저 반환하고 블록을 생성하므로, receipt-timeout 동안 영수증을 기다린다.
	 */
	public Transfer transfer(String from, String to, BigInteger wei) {
		String txHash = call("eth_sendTransaction", List.of(new TransactionRequest(from, to, Eth.toHex(wei))),
				new ParameterizedTypeReference<JsonRpcResponse<String>>() {});
		return Transfer.of(txHash, from, to, wei, waitForReceipt(txHash));
	}

	/**
	 * 트랜잭션 상세 정보를 조회한다. 블록에 포함되었으면 영수증과 블록 시각도 함께 조회한다.
	 * @throws TransactionNotFoundException 노드가 모르는 트랜잭션인 경우
	 */
	public TransactionDetail getTransaction(String txHash) {
		RpcTransaction tx = call("eth_getTransactionByHash", List.of(txHash),
				new ParameterizedTypeReference<JsonRpcResponse<RpcTransaction>>() {});
		if (tx == null) {
			throw new TransactionNotFoundException(txHash);
		}
		TransactionReceipt receipt = getReceipt(txHash);
		RpcBlock block = (receipt != null) ? call("eth_getBlockByNumber", List.of(receipt.blockNumber(), false),
				new ParameterizedTypeReference<JsonRpcResponse<RpcBlock>>() {}) : null;
		return TransactionDetail.of(tx, receipt, block);
	}

	private TransactionReceipt getReceipt(String txHash) {
		return call("eth_getTransactionReceipt", List.of(txHash),
				new ParameterizedTypeReference<JsonRpcResponse<TransactionReceipt>>() {});
	}

	/**
	 * 영수증이 나올 때까지 조회한다. 시간 안에 나오지 않으면 null.
	 */
	private TransactionReceipt waitForReceipt(String txHash) {
		long deadline = System.nanoTime() + receiptTimeout.toNanos();
		while (true) {
			TransactionReceipt receipt = getReceipt(txHash);
			if (receipt != null || System.nanoTime() >= deadline) {
				return receipt;
			}
			try {
				Thread.sleep(RECEIPT_POLL_INTERVAL);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return null;
			}
		}
	}

	/**
	 * JSON-RPC를 호출한다.
	 * @throws ChainUnavailableException 노드에 연결할 수 없는 경우 (연결 거부, timeout 등)
	 * @throws ChainException 노드가 오류를 반환한 경우
	 */
	private <T> T call(String method, List<?> params, ParameterizedTypeReference<JsonRpcResponse<T>> type) {
		JsonRpcResponse<T> response;
		try {
			response = restClient.post()
				.contentType(MediaType.APPLICATION_JSON)
				.body(new JsonRpcRequest("2.0", requestId.incrementAndGet(), method, params))
				.retrieve()
				.body(type);
		}
		catch (ResourceAccessException ex) {
			throw new ChainUnavailableException(ex);
		}
		if (response == null) {
			throw new ChainException(method + ": empty response");
		}
		if (response.error() != null) {
			throw new ChainException(method + ": " + response.error().message());
		}
		return response.result();
	}

	public record JsonRpcRequest(String jsonrpc, long id, String method, List<?> params) {
	}

	public record JsonRpcResponse<T>(T result, JsonRpcError error) {
	}

	public record JsonRpcError(int code, String message) {
	}

	public record TransactionRequest(String from, String to, String value) {
	}

	/**
	 * 트랜잭션 영수증 중 필요한 필드. 아직 블록에 포함되지 않았으면 노드가 null을 반환한다.
	 */
	public record TransactionReceipt(String blockNumber, String status, String gasUsed, String effectiveGasPrice) {
	}

	/**
	 * eth_getTransactionByHash 결과 중 필요한 필드. 블록에 포함되기 전이면 blockNumber가 null이다.
	 */
	public record RpcTransaction(String hash, String from, String to, String value, String nonce, String gas,
			String gasPrice, String blockNumber, String input) {
	}

	public record RpcBlock(String timestamp) {
	}

}
