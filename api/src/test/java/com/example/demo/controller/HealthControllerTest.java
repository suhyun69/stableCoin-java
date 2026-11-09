package com.example.demo.controller;
import com.example.demo.usecase.ReadChainInfoUseCase;
import com.example.demo.common.web.GlobalExceptionHandler;
import com.example.demo.common.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class HealthControllerTest {
    @Test void distinguishesLivenessFromChainAvailability() throws Exception {
        var chain=mock(ReadChainInfoUseCase.class);when(chain.chainId()).thenReturn(31337L);
        var mvc=MockMvcBuilders.standaloneSetup(new HealthController(),new ChainHealthController(chain)).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
        mvc.perform(get("/dependencies/chain")).andExpect(status().isOk()).andExpect(jsonPath("$.chainId").value(31337));
        when(chain.chainId()).thenThrow(new ServiceUnavailableException("chain unavailable",new IllegalStateException("offline")));
        mvc.perform(get("/dependencies/chain")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/health")).andExpect(status().isOk());
    }
}
