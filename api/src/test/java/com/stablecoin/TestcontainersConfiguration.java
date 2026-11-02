package com.stablecoin;

import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;

/**
 * 테스트용 Postgres 컨테이너. @ServiceConnection 으로 spring.datasource.* 가 이 컨테이너를 가리킨다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgres() {
		return new PostgreSQLContainer("postgres:17-alpine");
	}

}
