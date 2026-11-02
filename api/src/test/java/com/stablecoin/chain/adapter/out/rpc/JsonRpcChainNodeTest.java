package com.stablecoin.chain.adapter.out.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;

import com.stablecoin.chain.application.ChainException;
import com.stablecoin.chain.application.ChainUnavailableException;
import com.stablecoin.chain.domain.ChainReceipt;
import com.stablecoin.chain.domain.ChainTransaction;

/**
 * JSON-RPC 요청 형식과 hex 응답 → 도메인 값 변환을 확인한다.
 */
@RestClientTest(components = JsonRpcChainNode.class, properties = "chain.rpc-url=http://node.test")
class JsonRpcChainNodeTest {

	private static final String FROM = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";

	private static final String TO = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";

	@Autowired
	private JsonRpcChainNode chainNode;

	@Autowired
	private MockRestServiceServer server;

	@Test
	void getChainId() {
		expectRpc("eth_chainId", "\"0x7a69\"");

		assertThat(chainNode.getChainId()).isEqualTo(31337L);
	}

	@Test
	void getAccountsAndBalance() {
		expectRpc("eth_accounts", "[\"" + FROM + "\"]");
		server.expect(requestTo("http://node.test"))
			.andExpect(jsonPath("$.method").value("eth_getBalance"))
			.andExpect(jsonPath("$.params[0]").value(FROM))
			.andExpect(jsonPath("$.params[1]").value("latest"))
			.andRespond(result("\"0x21e19e0c9bab2400000\""));

		assertThat(chainNode.getAccounts()).containsExactly(FROM);
		assertThat(chainNode.getBalance(FROM)).isEqualTo(new BigInteger("10000000000000000000000"));
		server.verify();
	}

	@Test
	void getPendingNonceUsesPendingBlockTag() {
		server.expect(requestTo("http://node.test"))
			.andExpect(jsonPath("$.method").value("eth_getTransactionCount"))
			.andExpect(jsonPath("$.params[0]").value(FROM))
			.andExpect(jsonPath("$.params[1]").value("pending"))
			.andRespond(result("\"0x3\""));

		assertThat(chainNode.getPendingNonce(FROM)).isEqualTo(BigInteger.valueOf(3));
	}

	@Test
	void estimateGasSendsCallObject() {
		server.expect(requestTo("http://node.test"))
			.andExpect(jsonPath("$.method").value("eth_estimateGas"))
			.andExpect(jsonPath("$.params[0].from").value(FROM))
			.andExpect(jsonPath("$.params[0].to").value(TO))
			.andExpect(jsonPath("$.params[0].value").value("0x14d1120d7b160000"))
			.andRespond(result("\"0x5208\""));

		assertThat(chainNode.estimateGas(FROM, TO, new BigInteger("1500000000000000000")))
			.isEqualTo(BigInteger.valueOf(21000));
	}

	@Test
	void feeValues() {
		expectRpc("eth_maxPriorityFeePerGas", "\"0x3b9aca00\"");
		server.expect(requestTo("http://node.test"))
			.andExpect(jsonPath("$.method").value("eth_getBlockByNumber"))
			.andExpect(jsonPath("$.params[0]").value("latest"))
			.andExpect(jsonPath("$.params[1]").value(false))
			.andRespond(result("{\"number\":\"0x1\",\"timestamp\":\"0x1\",\"baseFeePerGas\":\"0x342770c0\"}"));

		assertThat(chainNode.getMaxPriorityFeePerGas()).isEqualTo(BigInteger.valueOf(1_000_000_000L));
		assertThat(chainNode.getLatestBaseFeePerGas()).isEqualTo(BigInteger.valueOf(875_000_000L));
		server.verify();
	}

	@Test
	void sendRawTransaction() {
		server.expect(requestTo("http://node.test"))
			.andExpect(jsonPath("$.method").value("eth_sendRawTransaction"))
			.andExpect(jsonPath("$.params[0]").value("0x02f8"))
			.andRespond(result("\"0xabc\""));

		assertThat(chainNode.sendRawTransaction("0x02f8")).isEqualTo("0xabc");
	}

	@Test
	void getTransactionMapsHexToDomain() {
		expectRpc("eth_getTransactionByHash", """
				{"hash":"0xabc","from":"%s","to":"%s","value":"0x14d1120d7b160000","nonce":"0x3","gas":"0x5209",
				"gasPrice":"0x77359400","blockNumber":"0x5","input":"0x"}
				""".formatted(FROM, TO));

		assertThat(chainNode.getTransaction("0xabc")).contains(new ChainTransaction("0xabc", FROM, TO,
				new BigInteger("1500000000000000000"), 3L, 21001L, BigInteger.valueOf(2_000_000_000L), 5L, "0x"));
	}

	@Test
	void unknownTransactionIsEmpty() {
		expectRpc("eth_getTransactionByHash", "null");

		assertThat(chainNode.getTransaction("0xabc")).isEmpty();
	}

	@Test
	void getReceiptMapsStatus() {
		expectRpc("eth_getTransactionReceipt", """
				{"blockNumber":"0x5","status":"0x0","gasUsed":"0x5208","effectiveGasPrice":"0x3b9aca00"}
				""");
		expectRpc("eth_getTransactionReceipt", "null");

		assertThat(chainNode.getReceipt("0xabc")).contains(
				new ChainReceipt(5L, false, BigInteger.valueOf(21000), BigInteger.valueOf(1_000_000_000L)));
		assertThat(chainNode.getReceipt("0xdef")).isEmpty();
	}

	@Test
	void getBlockTimestamp() {
		server.expect(requestTo("http://node.test"))
			.andExpect(jsonPath("$.method").value("eth_getBlockByNumber"))
			.andExpect(jsonPath("$.params[0]").value("0x5"))
			.andRespond(result("{\"number\":\"0x5\",\"timestamp\":\"0x6730c800\"}"));

		assertThat(chainNode.getBlockTimestamp(5L)).isEqualTo(Instant.ofEpochSecond(0x6730c800L));
	}

	@Test
	void connectionFailureThrowsChainUnavailable() {
		server.expect(requestTo("http://node.test")).andRespond(request -> {
			throw new IOException("Connection refused");
		});

		assertThatThrownBy(() -> chainNode.getChainId()).isInstanceOf(ChainUnavailableException.class)
			.hasCauseInstanceOf(ResourceAccessException.class);
	}

	@Test
	void rpcErrorThrowsChainException() {
		server.expect(requestTo("http://node.test"))
			.andRespond(withSuccess("""
					{"jsonrpc":"2.0","id":1,"error":{"code":-32003,"message":"Insufficient funds for gas * price + value"}}
					""", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> chainNode.estimateGas(FROM, TO, BigInteger.ONE)).isInstanceOf(ChainException.class)
			.hasMessage("eth_estimateGas: Insufficient funds for gas * price + value");
	}

	private void expectRpc(String rpcMethod, String resultJson) {
		server.expect(requestTo("http://node.test"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(jsonPath("$.method").value(rpcMethod))
			.andRespond(result(resultJson));
	}

	private static org.springframework.test.web.client.ResponseCreator result(String resultJson) {
		return withSuccess("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":" + resultJson + "}", MediaType.APPLICATION_JSON);
	}

}
