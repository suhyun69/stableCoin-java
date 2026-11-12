package com.example.demo.reconciliation.domain;

public record BlockRange(long from, long to) {
  public BlockRange {
    if (from < 0 || to < from || to - from >= 1000)
      throw new IllegalArgumentException("block range must contain 1 to 1000 blocks");
  }

  public static BlockRange parse(String from, String to) {
    if (from == null || to == null || !from.matches("[0-9]{1,19}") || !to.matches("[0-9]{1,19}"))
      throw new IllegalArgumentException("invalid block range");
    try {
      return new BlockRange(Long.parseLong(from), Long.parseLong(to));
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("block number exceeds supported range", e);
    }
  }
}
