package com.eventledger.model;

import java.math.BigDecimal;

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
