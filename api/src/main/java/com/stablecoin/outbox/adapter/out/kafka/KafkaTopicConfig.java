package com.stablecoin.outbox.adapter.out.kafka;

import org.apache.kafka.clients.admin.NewTopic;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * 앱이 시작할 때 지급 이벤트 토픽을 만든다 (이미 있으면 그대로 둔다). 로컬 단일 브로커라 복제본은 1개다.
 */
@Configuration
class KafkaTopicConfig {

	@Bean
	NewTopic payoutEventsTopic(@Value("${outbox.kafka.topic-prefix}") String topicPrefix) {
		return TopicBuilder.name(KafkaOutboxEventPublisher.topicFor(topicPrefix, "PAYOUT"))
			.partitions(3)
			.replicas(1)
			.build();
	}

}
