package com.example.demo.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.demo.chain.application.port.out.EthChainPort;
import com.example.demo.common.web.GlobalExceptionHandler;
import com.example.demo.usecase.impl.ReadWalletsUseCaseImpl;
import java.math.BigInteger;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WalletControllerTest {
  @Test
  void returnsRegisteredAddressesWithExactBalancesAtSameBlockAndReportsRpcFailure()
      throws Exception {
    var chain = mock(EthChainPort.class);
    String a = "0x" + "11".repeat(20), b = "0x" + "22".repeat(20), c = "0x" + "33".repeat(20);
    when(chain.chainId()).thenReturn(31337L);
    when(chain.head()).thenReturn(123L);
    when(chain.senders()).thenReturn(Set.of(c, b, a));
    when(chain.balance(a, 123)).thenReturn(BigInteger.ZERO);
    when(chain.balance(b, 123)).thenReturn(BigInteger.ONE);
    when(chain.balance(c, 123)).thenReturn(new BigInteger("10000000000000000000001"));
    var mvc =
        MockMvcBuilders.standaloneSetup(new WalletController(new ReadWalletsUseCaseImpl(chain)))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    mvc.perform(get("/wallets"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chainId").value(31337))
        .andExpect(jsonPath("$.blockNumber").value("123"))
        .andExpect(jsonPath("$.wallets.length()").value(3))
        .andExpect(jsonPath("$.wallets[0].address").value(a))
        .andExpect(jsonPath("$.wallets[0].balanceEth").value("0"))
        .andExpect(jsonPath("$.wallets[0].balanceWei").value("0"))
        .andExpect(jsonPath("$.wallets[1].balanceEth").value("0.000000000000000001"))
        .andExpect(jsonPath("$.wallets[2].balanceEth").value("10000.000000000000000001"))
        .andExpect(jsonPath("$.wallets[2].balanceWei").value("10000000000000000000001"))
        .andExpect(jsonPath("$.wallets[0].privateKey").doesNotExist());
    verify(chain).balance(a, 123);
    verify(chain).balance(b, 123);
    verify(chain).balance(c, 123);
    when(chain.balance(b, 123)).thenThrow(new IllegalStateException("RPC offline"));
    mvc.perform(get("/wallets")).andExpect(status().isServiceUnavailable());
  }
}
