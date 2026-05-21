package com.eventledger;

import com.eventledger.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ContractVerificationTest {

    private static final String SPEC = "http://localhost/v3/api-docs";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
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
                .andExpect(openApi().isValid(SPEC));
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
                .andExpect(openApi().isValid(SPEC));
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
                .andExpect(openApi().isValid(SPEC));
    }

    @Test
    void listEvents_responseMatchesContract() throws Exception {
        mockMvc.perform(get("/events").param("account", "acct-cv"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(SPEC));
    }

    @Test
    void getBalance_responseMatchesContract() throws Exception {
        mockMvc.perform(get("/accounts/acct-cv/balance"))
                .andExpect(status().isOk())
                .andExpect(openApi().isValid(SPEC));
    }

    @Test
    void invalidRequest_errorResponseMatchesContract() throws Exception {
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(openApi().isValid(SPEC));
    }
}
