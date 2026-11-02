package com.stablecoin.chain;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

@WebMvcTest(ChainController.class)
class ChainControllerTest {

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

}
