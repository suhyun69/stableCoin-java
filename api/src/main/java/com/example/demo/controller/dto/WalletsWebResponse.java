package com.example.demo.controller.dto;

import java.util.List;

public record WalletsWebResponse(
    long chainId, String blockNumber, List<WalletWebResponse> wallets) {
  public record WalletWebResponse(String address, String balanceEth, String balanceWei) {}
}
