package com.stablecoin.chain.adapter.out.rpc;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.stablecoin.chain.application.ChainException;
import com.stablecoin.chain.application.ChainUnavailableException;
import com.stablecoin.chain.application.port.out.ChainNodePort;
import com.stablecoin.chain.domain.ChainReceipt;
import com.stablecoin.chain.domain.ChainTransaction;
import com.stablecoin.chain.domain.Eth;

/**
 * 노드의 JSON-RPC를 RestClient로 호출하는 {@link ChainNodePort} 구현. hex 문자열을 도메인 값으로 바꾼다.
 */
@Component
public class JsonRpcChainNode implements ChainNodePort {

	private final RestClient restClient;

	private final AtomicLong requestId = new AtomicLong();

	public JsonRpcChainNode(RestClient.Builder builder, @Value("${chain.rpc-url}") String rpcUrl) {
		this.restClient = builder.baseUrl(rpcUrl).build();
	}

	@Override
	public long getChainId() {
		return Eth.fromHexLong(call("eth_chainId", List.of(), STRING));
	}

	@Override
	public List<String> getAccounts() {
		return call("eth_accounts", List.of(), new ParameterizedTypeReference<JsonRpcResponse<List<String>>>() {});
	}

	@Override
	public BigInteger getBalance(String address) {
		return Eth.fromHex(call("eth_getBalance", List.of(address, "latest"), STRING));
	}

	@Override
	public BigInteger getPendingNonce(String address) {
		return Eth.fromHex(call("eth_getTransactionCount", List.of(address, "pending"), STRING));
	}

	@Override
	public BigInteger estimateGas(String from, String to, BigInteger value) {
		return Eth.fromHex(call("eth_estimateGas", List.of(new CallRequest(from, to, Eth.toHex(value))), STRING));
	}

	@Override
	public BigInteger getMaxPriorityFeePerGas() {
		return Eth.fromHex(call("eth_maxPriorityFeePerGas", List.of(), STRING));
	}

	@Override
	public BigInteger getLatestBaseFeePerGas() {
		RpcBlock block = call("eth_getBlockByNumber", List.of("latest", false), BLOCK);
		if (block == null || block.baseFeePerGas() == null) {
			throw new ChainException("eth_getBlockByNumber: latest block has no baseFeePerGas");
		}
		return Eth.fromHex(block.baseFeePerGas());
	}

	@Override
	public String sendRawTransaction(String signedTransaction) {
		return call("eth_sendRawTransaction", List.of(signedTransaction), STRING);
	}

	@Override
	public Optional<ChainTransaction> getTransaction(String txHash) {
		RpcTransaction tx = call("eth_getTransactionByHash", List.of(txHash),
				new ParameterizedTypeReference<JsonRpcResponse<RpcTransaction>>() {});
		return Optional.ofNullable(tx).map(RpcTransaction::toDomain);
	}

	@Override
	public Optional<ChainReceipt> getReceipt(String txHash) {
		RpcReceipt receipt = call("eth_getTransactionReceipt", List.of(txHash),
				new ParameterizedTypeReference<JsonRpcResponse<RpcReceipt>>() {});
		return Optional.ofNullable(receipt).map(RpcReceipt::toDomain);
	}

	@Override
	public Instant getBlockTimestamp(long blockNumber) {
		RpcBlock block = call("eth_getBlockByNumber", List.of(Eth.toHex(BigInteger.valueOf(blockNumber)), false),
				BLOCK);
		if (block == null) {
			throw new ChainException("eth_getBlockByNumber: block " + blockNumber + " not found");
		}
		return Instant.ofEpochSecond(Eth.fromHexLong(block.timestamp()));
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

	private static final ParameterizedTypeReference<JsonRpcResponse<String>> STRING = new ParameterizedTypeReference<>() {};

	private static final ParameterizedTypeReference<JsonRpcResponse<RpcBlock>> BLOCK = new ParameterizedTypeReference<>() {};

	record JsonRpcRequest(String jsonrpc, long id, String method, List<?> params) {
	}

	record JsonRpcResponse<T>(T result, JsonRpcError error) {
	}

	record JsonRpcError(int code, String message) {
	}

	record CallRequest(String from, String to, String value) {
	}

	record RpcTransaction(String hash, String from, String to, String value, String nonce, String gas,
			String gasPrice, String blockNumber, String input) {

		ChainTransaction toDomain() {
			return new ChainTransaction(hash, from, to, Eth.fromHex(value), Eth.fromHexLong(nonce),
					Eth.fromHexLong(gas), (gasPrice != null) ? Eth.fromHex(gasPrice) : null,
					Eth.fromHexLong(blockNumber), input);
		}

	}

	record RpcReceipt(String blockNumber, String status, String gasUsed, String effectiveGasPrice) {

		ChainReceipt toDomain() {
			return new ChainReceipt(Eth.fromHexLong(blockNumber), "0x1".equals(status), Eth.fromHex(gasUsed),
					Eth.fromHex(effectiveGasPrice));
		}

	}

	record RpcBlock(String timestamp, String baseFeePerGas) {
	}

}
