package com.example.demo.payout.domain;

import com.example.demo.common.exception.InvalidInputException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record PayoutRequest(
    String merchantId,
    EthereumAddress senderAddress,
    EthereumAddress recipientAddress,
    EthAmount amount,
    String idempotencyKey) {
  public PayoutRequest {
    if (merchantId == null || merchantId.isBlank() || merchantId.length() > 200) {
      throw new InvalidInputException("merchantId is required and must be at most 200 characters");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 200) {
      throw new InvalidInputException(
          "Idempotency-Key is required and must be at most 200 characters");
    }
    if (senderAddress == null || recipientAddress == null || amount == null) {
      throw new InvalidInputException("senderAddress, recipientAddress and amount are required");
    }
  }

  public String fingerprint() {
    return hash("v2:" + senderAddress.value() + ":" + legacyCanonical());
  }

  public String legacyFingerprint() {
    return hash(legacyCanonical());
  }

  private String legacyCanonical() {
    // Versioned and length-prefixed merchant field; canonical address and wei.
    String canonical =
        "v1:"
            + merchantId.length()
            + ":"
            + merchantId
            + ":"
            + recipientAddress.value()
            + ":"
            + amount.wei();
    return canonical;
  }

  private static String hash(String canonical) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }
}
