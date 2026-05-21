package com.eventledger.controller;

import com.eventledger.model.BalanceResponse;
import com.eventledger.service.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventService eventService;

    @Test
    void getBalance_returns200WithBalanceResponse() throws Exception {
        when(eventService.getBalance("acct-123"))
                .thenReturn(new BalanceResponse("acct-123", new BigDecimal("250.00"), "USD"));

        mockMvc.perform(get("/accounts/acct-123/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acct-123"))
                .andExpect(jsonPath("$.balance").value(250.00))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void getBalance_zeroBalance_stillReturns200() throws Exception {
        when(eventService.getBalance("acct-unknown"))
                .thenReturn(new BalanceResponse("acct-unknown", BigDecimal.ZERO, "USD"));

        mockMvc.perform(get("/accounts/acct-unknown/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void getBalance_passesAccountIdToService() throws Exception {
        when(eventService.getBalance("acct-xyz"))
                .thenReturn(new BalanceResponse("acct-xyz", BigDecimal.ZERO, "USD"));

        mockMvc.perform(get("/accounts/acct-xyz/balance"))
                .andExpect(status().isOk());

        verify(eventService).getBalance("acct-xyz");
    }
}
