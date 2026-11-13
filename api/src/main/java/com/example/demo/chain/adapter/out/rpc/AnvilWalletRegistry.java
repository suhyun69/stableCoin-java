package com.example.demo.chain.adapter.out.rpc;

import com.example.demo.chain.application.port.out.WalletRegistryPort;
import com.example.demo.common.exception.InvalidInputException;
import java.util.*;
import org.web3j.crypto.Credentials;

public final class AnvilWalletRegistry implements WalletRegistryPort {
  private final Map<String, Credentials> wallets;

  public AnvilWalletRegistry(String keys) {
    if (keys == null || keys.isBlank())
      throw new IllegalArgumentException("Anvil private keys are required");
    Map<String, Credentials> values = new LinkedHashMap<>();
    for (String key : keys.split(",", -1)) {
      String normalized = key.trim();
      if (!normalized.matches("(0x)?[0-9a-fA-F]{64}"))
        throw new IllegalArgumentException("Invalid Anvil private key configuration");
      var scalar = new java.math.BigInteger(normalized.replaceFirst("^0x", ""), 16);
      if (scalar.signum() <= 0
          || scalar.compareTo(
                  new java.math.BigInteger(
                      "fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", 16))
              >= 0) throw new IllegalArgumentException("Invalid Anvil private key configuration");
      var credentials = Credentials.create(normalized);
      String address = credentials.getAddress().toLowerCase(Locale.ROOT);
      if (values.putIfAbsent(address, credentials) != null)
        throw new IllegalArgumentException("Duplicate Anvil account");
    }
    wallets = Collections.unmodifiableMap(values);
  }

  public Set<String> addresses() {
    return wallets.keySet();
  }

  public void requireRegistered(String address) {
    if (address == null || !wallets.containsKey(address.toLowerCase(Locale.ROOT)))
      throw new InvalidInputException("Wallet is not a registered Anvil account");
  }

  public Credentials credentials(String address) {
    requireRegistered(address);
    return wallets.get(address.toLowerCase(Locale.ROOT));
  }
}
