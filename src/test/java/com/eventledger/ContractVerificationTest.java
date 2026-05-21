package com.eventledger;

import com.eventledger.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// RANDOM_PORT starts a real embedded server so the spec URL is reachable via HTTP
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ContractVerificationTest {

    @LocalServerPort
    private int port;

    private String specUrl;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        specUrl = "http://localhost:" + port + "/v3/api-docs";
    }

    @Test
    void submitEvent_responseMatchesContract() throws Exception {
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "eventId": "cv-001",
                          "accountId": "acct-cv",
                          "type": "CREDIT",
                          "amount": 100.00,
                          "currency": "USD",
                          "eventTimestamp": "2026-05-15T10:00:00Z"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(openApi().isValid(specUrl));
    }

    @Test
    void submitEvent_duplicate_responseMatchesContract() throws Exception {
        String body = """
                {
                  "eventId": "cv-002",
                  "accountId": "acct-cv",
                  "type": "CREDIT",
                  "amount": 50.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T10:00:00Z"
                }
                """;
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body));
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(specUrl));
    }

    @Test
    void getEvent_responseMatchesContract() throws Exception {
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "eventId": "cv-003",
                          "accountId": "acct-cv",
                          "type": "DEBIT",
                          "amount": 25.00,
                          "currency": "USD",
                          "eventTimestamp": "2026-05-15T11:00:00Z"
                        }
                        """));
        mockMvc.perform(get("/events/cv-003"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(specUrl));
    }

    @Test
    void listEvents_responseMatchesContract() throws Exception {
        mockMvc.perform(get("/events").param("account", "acct-cv"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(specUrl));
    }

    @Test
    void getBalance_responseMatchesContract() throws Exception {
        mockMvc.perform(get("/accounts/acct-cv/balance"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(specUrl));
    }

    @Test
    void invalidRequest_returns400() throws Exception {
        // Sends an intentionally invalid body — the contract validator correctly rejects
        // the request itself, so we only assert the HTTP status here. The ErrorResponse
        // shape is covered by EventLedgerIntegrationTest.
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
