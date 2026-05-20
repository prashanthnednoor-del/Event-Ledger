package com.eventledger.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Event Ledger API",
        version = "1.0.0",
        description = "Receives financial transaction events from upstream systems. " +
                      "Enforces idempotency, tolerates out-of-order delivery, and computes accurate account balances."
    )
)
public class OpenApiConfig {}
