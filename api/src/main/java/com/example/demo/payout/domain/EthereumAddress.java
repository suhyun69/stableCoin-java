package com.example.demo.payout.domain;

import com.example.demo.common.exception.InvalidInputException;
import java.util.Locale;

/** Validates hexadecimal address format; normalizes identity to lowercase. */
public record EthereumAddress(String value) {
  public EthereumAddress {
    if (value == null || !value.matches("0x[0-9a-fA-F]{40}")) {
      throw new InvalidInputException("wallet address must be a 20-byte hexadecimal address");
    }
    value = value.toLowerCase(Locale.ROOT);
    if (value.equals("0x" + "0".repeat(40))) {
      throw new InvalidInputException("zero address is not allowed");
    }
  }
}
