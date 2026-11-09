package com.example.demo.outbox.domain;
public record OutboxMessage(String id, String payoutId, String payload, String owner, int attempts) {}
