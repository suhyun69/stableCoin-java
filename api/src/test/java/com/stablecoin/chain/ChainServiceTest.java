package com.stablecoin.chain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;

@RestClientTest(components = ChainService.class,
		properties = { "chain.rpc-url=http://node.test", "chain.receipt-timeout=300ms" })
class ChainServiceTest {

	private static final String FROM = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";

	private static final String TO = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";

	@Autowired
	private ChainService chainService;

	@Autowired
	private MockRestServiceServer server;

	@Test
	void getAccountsReturnsAddressesWithBalances() {
		expectRpc("eth_accounts", """
				{"jsonrpc":"2.0","id":1,"result":["0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"]}
				""");
		expectRpc("eth_getBalance", """
				{"jsonrpc":"2.0","id":2,"result":"0x21e19e0c9bab2400000"}
				""");

		List<Account> accounts = chainService.getAccounts();

		assertThat(accounts).containsExactly(new Account("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266",
				"10000000000000000000000", "10000"));
		server.verify();
	}

	@Test
	void transferSendsTransactionAndReturnsReceipt() {
		server.expect(requestTo("http://node.test"))
			.andExpect(jsonPath("$.method").value("eth_sendTransaction"))
			.andExpect(jsonPath("$.params[0].from").value(FROM))
			.andExpect(jsonPath("$.params[0].to").value(TO))
			.andExpect(jsonPath("$.params[0].value").value("0x14d1120d7b160000"))
			.andRespond(withSuccess("""
					{"jsonrpc":"2.0","id":1,"result":"0xabc"}
					""", MediaType.APPLICATION_JSON));
		server.expect(requestTo("http://node.test"))
			.andExpect(jsonPath("$.method").value("eth_getTransactionReceipt"))
			.andExpect(jsonPath("$.params[0]").value("0xabc"))
			.andRespond(withSuccess("""
					{"jsonrpc":"2.0","id":2,"result":{"blockNumber":"0x5","status":"0x1","gasUsed":"0x5208"}}
					""", MediaType.APPLICATION_JSON));

		Transfer transfer = chainService.transfer(FROM, TO, new BigInteger("1500000000000000000"));

		assertThat(transfer).isEqualTo(new Transfer("0xabc", FROM, TO, "1500000000000000000", "1.5", 5L,
				TransactionStatus.SUCCESS));
		server.verify();
	}

	@Test
	void transferWaitsUntilReceiptIsAvailable() {
		expectRpc("eth_sendTransaction", """
				{"jsonrpc":"2.0","id":1,"result":"0xabc"}
				""");
		expectRpc("eth_getTransactionReceipt", """
				{"jsonrpc":"2.0","id":2,"result":null}
				""");
		expectRpc("eth_getTransactionReceipt", """
				{"jsonrpc":"2.0","id":3,"result":{"blockNumber":"0x2","status":"0x0"}}
				""");

		Transfer transfer = chainService.transfer(FROM, TO, BigInteger.ONE);

		assertThat(transfer.status()).isEqualTo(TransactionStatus.FAILED);
		assertThat(transfer.blockNumber()).isEqualTo(2L);
		server.verify();
	}

	@Test
	void transferWithoutReceiptBeforeTimeoutIsPending() {
		expectRpc("eth_sendTransaction", """
				{"jsonrpc":"2.0","id":1,"result":"0xabc"}
				""");
		server.expect(ExpectedCount.manyTimes(), requestTo("http://node.test"))
			.andExpect(jsonPath("$.method").value("eth_getTransactionReceipt"))
			.andRespond(withSuccess("""
					{"jsonrpc":"2.0","id":2,"result":null}
					""", MediaType.APPLICATION_JSON));

		Transfer transfer = chainService.transfer(FROM, TO, BigInteger.ONE);

		assertThat(transfer.status()).isEqualTo(TransactionStatus.PENDING);
		assertThat(transfer.blockNumber()).isNull();
	}

	@Test
	void getMinedTransaction() {
		expectRpc("eth_getTransactionByHash", """
				{"jsonrpc":"2.0","id":1,"result":{"hash":"0xabc","from":"%s","to":"%s","value":"0x14d1120d7b160000",
				"nonce":"0x3","gas":"0x5209","gasPrice":"0x77359400","blockNumber":"0x5","input":"0x"}}
				""".formatted(FROM, TO));
		expectRpc("eth_getTransactionReceipt", """
				{"jsonrpc":"2.0","id":2,"result":{"blockNumber":"0x5","status":"0x1","gasUsed":"0x5208",
				"effectiveGasPrice":"0x3b9aca00"}}
				""");
		server.expect(requestTo("http://node.test"))
			.andExpect(jsonPath("$.method").value("eth_getBlockByNumber"))
			.andExpect(jsonPath("$.params[0]").value("0x5"))
			.andExpect(jsonPath("$.params[1]").value(false))
			.andRespond(withSuccess("""
					{"jsonrpc":"2.0","id":3,"result":{"number":"0x5","timestamp":"0x6730c800"}}
					""", MediaType.APPLICATION_JSON));

		TransactionDetail detail = chainService.getTransaction("0xabc");

		assertThat(detail).isEqualTo(new TransactionDetail("0xabc", TransactionStatus.SUCCESS, 5L,
				Instant.ofEpochSecond(0x6730c800L), FROM, TO, "1500000000000000000", "1.5", 3L, 21001L, 21000L,
				"1000000000", "21000000000000", "0.000021", "0x"));
		server.verify();
	}

	@Test
	void getPendingTransaction() {
		expectRpc("eth_getTransactionByHash", """
				{"jsonrpc":"2.0","id":1,"result":{"hash":"0xabc","from":"%s","to":"%s","value":"0x1",
				"nonce":"0x0","gas":"0x5209","gasPrice":"0x77359400","blockNumber":null,"input":"0x"}}
				""".formatted(FROM, TO));
		expectRpc("eth_getTransactionReceipt", """
				{"jsonrpc":"2.0","id":2,"result":null}
				""");

		TransactionDetail detail = chainService.getTransaction("0xabc");

		assertThat(detail.status()).isEqualTo(TransactionStatus.PENDING);
		assertThat(detail.blockNumber()).isNull();
		assertThat(detail.timestamp()).isNull();
		assertThat(detail.gasUsed()).isNull();
		assertThat(detail.feeWei()).isNull();
		assertThat(detail.gasPriceWei()).isEqualTo("2000000000");
		server.verify();
	}

	@Test
	void unknownTransactionThrowsNotFound() {
		expectRpc("eth_getTransactionByHash", """
				{"jsonrpc":"2.0","id":1,"result":null}
				""");

		assertThatThrownBy(() -> chainService.getTransaction("0xabc"))
			.isInstanceOf(TransactionNotFoundException.class);
		server.verify();
	}

	@Test
	void rpcErrorThrowsChainException() {
		expectRpc("eth_accounts", """
				{"jsonrpc":"2.0","id":1,"error":{"code":-32601,"message":"Method not found"}}
				""");

		assertThatThrownBy(() -> chainService.getAccounts()).isInstanceOf(ChainException.class)
			.hasMessage("eth_accounts: Method not found");
	}

	private void expectRpc(String rpcMethod, String responseBody) {
		server.expect(requestTo("http://node.test"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(jsonPath("$.method").value(rpcMethod))
			.andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
	}

}
