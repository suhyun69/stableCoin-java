package com.stablecoin.payout.adapter.in.kafka;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * 소비자 처리 실패 시 같은 메시지를 1s, 2s, 4s ... (최대 30s 간격, 총 5분) 다시 처리한다.
 * 그래도 실패하면 로그만 남기고 넘어간다. 그 지급은 전송 기록(STARTED/SIGNED)이 남아 있어 resumeStuck 이 이어간다.
 */
@Configuration
class KafkaConsumerConfig {

	@Bean
	DefaultErrorHandler kafkaErrorHandler() {
		ExponentialBackOff backOff = new ExponentialBackOff(1_000, 2.0);
		backOff.setMaxInterval(30_000);
		backOff.setMaxElapsedTime(300_000);
		return new DefaultErrorHandler(backOff);
	}

}
