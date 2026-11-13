package com.example.demo.chain.application.port.out;

import java.util.Set;

public interface WalletRegistryPort {
  Set<String> addresses();

  void requireRegistered(String address);
}
