package com.stablecoin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "chain.rpc-url=http://127.0.0.1:1")
class StablecoinApplicationTests {

	@Test
	void contextLoads() {
	}

}
