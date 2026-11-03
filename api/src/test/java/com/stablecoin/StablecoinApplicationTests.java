package com.stablecoin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = { "chain.rpc-url=http://127.0.0.1:1", "outbox.publisher.enabled=false" })
@Import(TestcontainersConfiguration.class)
class StablecoinApplicationTests {

	@Test
	void contextLoads() {
	}

}
