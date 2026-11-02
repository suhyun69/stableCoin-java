package com.stablecoin.chain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

@RestClientTest(components = ChainService.class, properties = "chain.rpc-url=http://node.test")
class ChainServiceTest {

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
