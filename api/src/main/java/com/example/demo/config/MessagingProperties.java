package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("stablepay.messaging")
public record MessagingProperties(
    @DefaultValue("stablepay.payout-requested.v1") String topic,
    @DefaultValue("stablepay-payout-sender") String group,
    @DefaultValue("1") int partitions) {
  public MessagingProperties {
    if (topic == null || topic.isBlank() || group == null || group.isBlank() || partitions < 1)
      throw new IllegalArgumentException("invalid messaging configuration");
  }

  public String reconciliationTopic() {
    return topic + ".reconciliation";
  }

  public String deadLetterTopic() {
    return topic + ".DLT";
  }
}
