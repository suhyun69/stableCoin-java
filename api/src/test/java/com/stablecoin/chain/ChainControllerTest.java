package com.stablecoin.chain;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

@WebMvcTest(ChainController.class)
class ChainControllerTest {

	private static final String FROM = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";

	private static final String TO = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";

	private static final String TX_HASH = "0x236660fa85ffc77d11a69d8ccbbe440d4182516d9bf1bbef0af6baa271b31a15";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ChainService chainService;

	@Test
	void getAccounts() throws Exception {
		given(chainService.getAccounts()).willReturn(List.of(
				Account.of("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266", new BigInteger("1500000000000000000"))));

		mockMvc.perform(get("/api/chain/accounts"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].address").value("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"))
			.andExpect(jsonPath("$[0].balanceWei").value("1500000000000000000"))
			.andExpect(jsonPath("$[0].balanceEth").value("1.5"));
	}

	@Test
	void nodeUnavailableReturnsServiceUnavailableWithRetryAfter() throws Exception {
		given(chainService.getAccounts()).willThrow(new ChainUnavailableException(
				new ResourceAccessException("I/O error on POST request for \"http://127.0.0.1:8545\"")));

		mockMvc.perform(get("/api/chain/accounts"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(header().string("Retry-After", "5"))
			.andExpect(jsonPath("$.code").value("CHAIN_UNAVAILABLE"))
			.andExpect(jsonPath("$.detail").value("chain is temporarily unavailable"));
	}

	@Test
	void unexpectedNodeResponseReturnsBadGatewayWithoutInternalDetails() throws Exception {
		given(chainService.getAccounts())
			.willThrow(new RestClientException("Error while extracting response from http://127.0.0.1:8545"));

		mockMvc.perform(get("/api/chain/accounts"))
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.code").value("CHAIN_ERROR"))
			.andExpect(jsonPath("$.detail").value("chain returned an unexpected response"));
	}

	@Test
	void transfer() throws Exception {
		BigInteger wei = new BigInteger("1500000000000000000");
		given(chainService.transfer(FROM, TO, wei))
			.willReturn(new Transfer("0xabc", FROM, TO, wei.toString(), "1.5", 1L, TransactionStatus.SUCCESS));

		mockMvc.perform(post("/api/chain/transfers").contentType(MediaType.APPLICATION_JSON).content("""
				{"from":"%s","to":"%s","amountEth":1.5}
				""".formatted(FROM, TO)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.txHash").value("0xabc"))
			.andExpect(jsonPath("$.amountWei").value("1500000000000000000"))
			.andExpect(jsonPath("$.amountEth").value("1.5"))
			.andExpect(jsonPath("$.blockNumber").value(1))
			.andExpect(jsonPath("$.status").value("SUCCESS"));
	}

	@ParameterizedTest
	@ValueSource(strings = { """
			{"from":"0x1234","to":"%s","amountEth":1}
			""", """
			{"to":"%s","amountEth":1}
			""", """
			{"from":"%2$s","to":"%1$s","amountEth":0}
			""", """
			{"from":"%2$s","to":"%1$s","amountEth":-1}
			""", """
			{"from":"%2$s","to":"%1$s","amountEth":0.0000000000000000001}
			""" })
	void invalidTransferRequestReturnsBadRequest(String body) throws Exception {
		mockMvc.perform(post("/api/chain/transfers").contentType(MediaType.APPLICATION_JSON)
			.content(body.formatted(TO, FROM)))
			.andExpect(status().isBadRequest());

		then(chainService).should(never()).transfer(any(), any(), any());
	}

	@Test
	void invalidTransferRequestDescribesFields() throws Exception {
		mockMvc.perform(post("/api/chain/transfers").contentType(MediaType.APPLICATION_JSON).content("""
				{"from":"0x1234","to":"%s","amountEth":0}
				""".formatted(TO)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.detail").value("amountEth: 0보다 커야 합니다, from: 올바른 지갑 주소가 아닙니다"));
	}

	@Test
	void transferRejectedByNodeReturnsBadRequest() throws Exception {
		given(chainService.transfer(any(), any(), any()))
			.willThrow(new ChainException("eth_sendTransaction: Insufficient funds for gas * price + value"));

		mockMvc.perform(post("/api/chain/transfers").contentType(MediaType.APPLICATION_JSON).content("""
				{"from":"%s","to":"%s","amountEth":100000}
				""".formatted(FROM, TO)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CHAIN_REJECTED"))
			.andExpect(jsonPath("$.detail").value("eth_sendTransaction: Insufficient funds for gas * price + value"));
	}

	@Test
	void getTransaction() throws Exception {
		given(chainService.getTransaction(TX_HASH)).willReturn(new TransactionDetail(TX_HASH,
				TransactionStatus.SUCCESS, 5L, Instant.parse("2026-11-02T12:00:00Z"), FROM, TO, "1500000000000000000",
				"1.5", 3L, 21001L, 21000L, "1000000000", "21000000000000", "0.000021", "0x"));

		mockMvc.perform(get("/api/chain/transactions/{txHash}", TX_HASH))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.txHash").value(TX_HASH))
			.andExpect(jsonPath("$.status").value("SUCCESS"))
			.andExpect(jsonPath("$.timestamp").value("2026-11-02T12:00:00Z"))
			.andExpect(jsonPath("$.valueEth").value("1.5"))
			.andExpect(jsonPath("$.feeWei").value("21000000000000"));
	}

	@Test
	void invalidTxHashReturnsBadRequest() throws Exception {
		mockMvc.perform(get("/api/chain/transactions/{txHash}", "0x1234"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.detail").value("txHash: 올바른 트랜잭션 해시가 아닙니다"));

		then(chainService).should(never()).getTransaction(any());
	}

	@Test
	void unknownTransactionReturnsNotFound() throws Exception {
		given(chainService.getTransaction(TX_HASH)).willThrow(new TransactionNotFoundException(TX_HASH));

		mockMvc.perform(get("/api/chain/transactions/{txHash}", TX_HASH))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"))
			.andExpect(jsonPath("$.detail").value("트랜잭션을 찾을 수 없습니다: " + TX_HASH));
	}

}
