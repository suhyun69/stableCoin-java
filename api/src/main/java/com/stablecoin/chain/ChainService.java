package com.stablecoin.chain;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * anvil 노드의 JSON-RPC를 호출해 체인 정보를 조회한다.
 */
@Service
public class ChainService {

	private final RestClient restClient;

	private final AtomicLong requestId = new AtomicLong();

	public ChainService(RestClient.Builder builder, @Value("${chain.rpc-url}") String rpcUrl) {
		this.restClient = builder.baseUrl(rpcUrl).build();
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
		return new BigInteger(hex.substring(2), 16);
	}

	private <T> T call(String method, List<?> params, ParameterizedTypeReference<JsonRpcResponse<T>> type) {
		JsonRpcResponse<T> response = restClient.post()
			.contentType(MediaType.APPLICATION_JSON)
			.body(new JsonRpcRequest("2.0", requestId.incrementAndGet(), method, params))
			.retrieve()
			.body(type);
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

}
