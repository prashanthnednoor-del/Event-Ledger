package com.eventledger.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Computed net balance for an account")
public class BalanceResponse {

    private String accountId;
    private BigDecimal balance;
    private String currency;

    public BalanceResponse(String accountId, BigDecimal balance, String currency) {
        this.accountId = accountId;
        this.balance = balance;
        this.currency = currency;
    }

    public String getAccountId() { return accountId; }
    public BigDecimal getBalance() { return balance; }
    public String getCurrency() { return currency; }
}
