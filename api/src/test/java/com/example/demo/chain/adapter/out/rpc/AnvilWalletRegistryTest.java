package com.example.demo.chain.adapter.out.rpc;

import static org.junit.jupiter.api.Assertions.*;

import com.example.demo.common.exception.InvalidInputException;
import org.junit.jupiter.api.Test;

class AnvilWalletRegistryTest {
  static final String KEY0 = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
  static final String KEY1 = "0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d";

  @Test
  void selectsCredentialsByAddressAndRejectsInvalidConfiguration() {
    var wallets = new AnvilWalletRegistry(KEY0 + ", " + KEY1);
    assertEquals(2, wallets.addresses().size());
    String from = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";
    assertEquals(from, wallets.credentials(from.toUpperCase()).getAddress());
    assertThrows(
        InvalidInputException.class, () -> wallets.requireRegistered("0x" + "ab".repeat(20)));
    for (String keys :
        new String[] {
          "", "bad", KEY0 + ",", KEY0 + "," + KEY0, "0x" + "0".repeat(64), "0x" + "f".repeat(64)
        }) assertThrows(IllegalArgumentException.class, () -> new AnvilWalletRegistry(keys));
    assertThrows(UnsupportedOperationException.class, () -> wallets.addresses().clear());
  }
}
