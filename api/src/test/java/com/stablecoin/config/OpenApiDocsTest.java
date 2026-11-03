package com.stablecoin.config;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.stablecoin.TestcontainersConfiguration;

@SpringBootTest(properties = { "chain.rpc-url=http://127.0.0.1:1", "outbox.publisher.enabled=false" })
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class OpenApiDocsTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void documentsActuatorHealthEndpoints() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths['/actuator/health'].get.tags").value(hasItem("Actuator")))
			.andExpect(jsonPath("$.paths['/actuator/health'].get.responses['503']").exists())
			.andExpect(jsonPath("$.paths['/actuator/health/chain'].get.summary").value("노드 연결 상태"))
			.andExpect(jsonPath("$.paths['/actuator/health/chain'].get.responses['200']").exists())
			.andExpect(jsonPath("$.paths['/actuator/health/chain'].get.responses['503']").exists());
	}

}
