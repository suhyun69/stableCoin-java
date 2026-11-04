package com.stablecoin.payout.adapter.in.web;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.stablecoin.payout.application.InvalidBlockRangeException;
import com.stablecoin.payout.application.port.in.RunReconciliationUseCase;

@WebMvcTest(ReconciliationController.class)
class ReconciliationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RunReconciliationUseCase reconciliation;

	@Test
	void acceptsARange() throws Exception {
		mockMvc.perform(post("/internal/reconciliation/runs").contentType(MediaType.APPLICATION_JSON)
			.content("{\"fromBlock\":1,\"toBlock\":5}"))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.fromBlock").value(1))
			.andExpect(jsonPath("$.toBlock").value(5));

		then(reconciliation).should().enqueue(1, 5);
	}

	@Test
	void invalidRangeReturnsBadRequest() throws Exception {
		willThrow(new InvalidBlockRangeException("fromBlock must be between 0 and toBlock")).given(reconciliation)
			.enqueue(5, 1);

		mockMvc.perform(post("/internal/reconciliation/runs").contentType(MediaType.APPLICATION_JSON)
			.content("{\"fromBlock\":5,\"toBlock\":1}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_BLOCK_RANGE"));
	}

	@Test
	void missingBlockReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/internal/reconciliation/runs").contentType(MediaType.APPLICATION_JSON)
			.content("{\"fromBlock\":1}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		then(reconciliation).should(never()).enqueue(anyLong(), anyLong());
	}

}
