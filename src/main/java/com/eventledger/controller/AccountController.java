package com.eventledger.controller;

import com.eventledger.model.BalanceResponse;
import com.eventledger.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Accounts", description = "Account balance operations")
@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final EventService eventService;

    public AccountController(EventService eventService) {
        this.eventService = eventService;
    }

    @Operation(
        summary = "Get current balance for an account",
        description = "Computes the net balance as sum(CREDIT) - sum(DEBIT) across all events for the account. Returns 0.00 if no events exist.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Balance computed successfully")
        }
    )
    @GetMapping("/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @Parameter(description = "The account ID", example = "acct-123")
            @PathVariable String accountId) {
        return ResponseEntity.ok(eventService.getBalance(accountId));
    }
}
