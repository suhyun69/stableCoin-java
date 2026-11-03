package com.stablecoin;

import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;

/**
 * 테스트용 Postgres, Kafka 컨테이너. @ServiceConnection 으로 spring.datasource.*, spring.kafka.* 가 이 컨테이너를 가리킨다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgres() {
		return new PostgreSQLContainer("postgres:17-alpine");
	}

	@Bean
	@ServiceConnection
	KafkaContainer kafka() {
		return new KafkaContainer("apache/kafka:4.1.1");
	}

}
