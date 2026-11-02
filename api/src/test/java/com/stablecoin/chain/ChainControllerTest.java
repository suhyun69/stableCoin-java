package com.stablecoin.chain;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigInteger;
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

@WebMvcTest(ChainController.class)
class ChainControllerTest {

	private static final String FROM = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";

	private static final String TO = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";

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
	void nodeUnavailableReturnsBadGateway() throws Exception {
		given(chainService.getAccounts()).willThrow(new ResourceAccessException("Connection refused"));

		mockMvc.perform(get("/api/chain/accounts"))
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.detail").value("Connection refused"));
	}

	@Test
	void transfer() throws Exception {
		BigInteger wei = new BigInteger("1500000000000000000");
		given(chainService.transfer(FROM, TO, wei))
			.willReturn(new Transfer("0xabc", FROM, TO, wei.toString(), "1.5", 1L, Transfer.Status.SUCCESS));

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
			.andExpect(jsonPath("$.detail").value("eth_sendTransaction: Insufficient funds for gas * price + value"));
	}

}
