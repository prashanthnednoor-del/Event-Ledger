package com.eventledger;

import com.eventledger.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class EventLedgerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
    }

    // ── Happy path ──────────────────────────────────────────────────────────────

    @Test
    void submitEvent_returnsCreatedWithFullBody() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("evt-001", "acct-1", "CREDIT", "150.00", "2026-05-15T10:00:00Z")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value("evt-001"))
                .andExpect(jsonPath("$.accountId").value("acct-1"))
                .andExpect(jsonPath("$.type").value("CREDIT"))
                .andExpect(jsonPath("$.amount").value(150.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.receivedAt").doesNotExist());
    }

    @Test
    void getEvent_returnsStoredEvent() throws Exception {
        postEvent("evt-002", "acct-1", "CREDIT", "50.00", "2026-05-15T10:00:00Z");

        mockMvc.perform(get("/events/evt-002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt-002"))
                .andExpect(jsonPath("$.amount").value(50.00));
    }

    // ── Idempotency ─────────────────────────────────────────────────────────────

    @Test
    void submitDuplicate_returnsOkWithOriginalEvent() throws Exception {
        String json = eventJson("evt-dup", "acct-1", "CREDIT", "100.00", "2026-05-15T10:00:00Z");

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());

        // Second submission with same eventId — must return 200, not 201
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt-dup"))
                .andExpect(jsonPath("$.amount").value(100.00));

        assertThat(eventRepository.count()).isEqualTo(1);
    }

    @Test
    void submitDuplicate_doesNotAlterBalance() throws Exception {
        String json = eventJson("evt-dup-bal", "acct-bal", "CREDIT", "200.00", "2026-05-15T10:00:00Z");

        postEvent("evt-dup-bal", "acct-bal", "CREDIT", "200.00", "2026-05-15T10:00:00Z");

        // Submit the same event again
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());

        // Balance must still be 200, not 400
        mockMvc.perform(get("/accounts/acct-bal/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(200.00));
    }

    @Test
    void concurrentDuplicates_onlyOneEventStored() throws Exception {
        int threadCount = 10;
        String json = eventJson("evt-concurrent", "acct-concurrent", "CREDIT", "100.00", "2026-05-15T10:00:00Z");

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                try {
                    ready.await(); // all threads fire at once
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return -1;
                }
                try {
                    return mockMvc.perform(post("/events")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        executor.shutdown();
        executor.awaitTermination(15, TimeUnit.SECONDS);

        for (Future<Integer> f : futures) {
            assertThat(f.get()).isIn(200, 201);
        }
        assertThat(eventRepository.count()).isEqualTo(1);
    }

    // ── Out-of-order tolerance ──────────────────────────────────────────────────

    @Test
    void outOfOrderArrival_listReturnsSortedByEventTimestamp() throws Exception {
        // Submit in reverse chronological order
        postEvent("evt-c", "acct-2", "CREDIT", "10.00", "2026-05-15T12:00:00Z");
        postEvent("evt-a", "acct-2", "CREDIT", "10.00", "2026-05-15T10:00:00Z");
        postEvent("evt-b", "acct-2", "CREDIT", "10.00", "2026-05-15T11:00:00Z");

        mockMvc.perform(get("/events").param("account", "acct-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value("evt-a"))
                .andExpect(jsonPath("$[1].eventId").value("evt-b"))
                .andExpect(jsonPath("$[2].eventId").value("evt-c"));
    }

    @Test
    void outOfOrderArrival_balanceIsStillCorrect() throws Exception {
        // Arrive in reverse order — balance must not depend on arrival order
        postEvent("evt-d3", "acct-4", "DEBIT",  "50.00",  "2026-05-15T12:00:00Z");
        postEvent("evt-c1", "acct-4", "CREDIT", "200.00", "2026-05-15T10:00:00Z");
        postEvent("evt-c2", "acct-4", "CREDIT", "100.00", "2026-05-15T11:00:00Z");

        // 200 + 100 - 50 = 250 regardless of arrival order
        mockMvc.perform(get("/accounts/acct-4/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(250.00));
    }

    // ── Balance computation ──────────────────────────────────────────────────────

    @Test
    void getBalance_correctNetBalance() throws Exception {
        postEvent("evt-cr1", "acct-3", "CREDIT", "300.00", "2026-05-15T10:00:00Z");
        postEvent("evt-cr2", "acct-3", "CREDIT", "200.00", "2026-05-15T11:00:00Z");
        postEvent("evt-db1", "acct-3", "DEBIT",  "150.00", "2026-05-15T12:00:00Z");

        // 300 + 200 - 150 = 350
        mockMvc.perform(get("/accounts/acct-3/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acct-3"))
                .andExpect(jsonPath("$.balance").value(350.00))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void getBalance_onlyDebits_returnsNegativeBalance() throws Exception {
        postEvent("evt-db-only", "acct-5", "DEBIT", "75.00", "2026-05-15T10:00:00Z");

        mockMvc.perform(get("/accounts/acct-5/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(-75.00));
    }

    @Test
    void getBalance_noEvents_returnsZero() throws Exception {
        mockMvc.perform(get("/accounts/acct-unknown/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0));
    }

    // ── Not found ────────────────────────────────────────────────────────────────

    @Test
    void getEvent_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/events/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    // ── Validation ───────────────────────────────────────────────────────────────

    @Test
    void submitEvent_missingEventId_returns400() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "acct-1",
                                  "type": "CREDIT",
                                  "amount": 100.00,
                                  "currency": "USD",
                                  "eventTimestamp": "2026-05-15T10:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.messages[0]").value(containsString("eventId")));
    }

    @Test
    void submitEvent_missingAccountId_returns400() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-bad",
                                  "type": "CREDIT",
                                  "amount": 100.00,
                                  "currency": "USD",
                                  "eventTimestamp": "2026-05-15T10:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages[0]").value(containsString("accountId")));
    }

    @Test
    void submitEvent_zeroAmount_returns400() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("evt-bad", "acct-1", "CREDIT", "0", "2026-05-15T10:00:00Z")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages[0]").value(containsString("amount")));
    }

    @Test
    void submitEvent_negativeAmount_returns400() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("evt-bad", "acct-1", "CREDIT", "-50.00", "2026-05-15T10:00:00Z")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages[0]").value(containsString("amount")));
    }

    @Test
    void submitEvent_invalidType_returns400WithMessage() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("evt-bad", "acct-1", "TRANSFER", "100.00", "2026-05-15T10:00:00Z")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages[0]").value(containsString("CREDIT or DEBIT")));
    }

    @Test
    void submitEvent_missingTimestamp_returns400() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-bad",
                                  "accountId": "acct-1",
                                  "type": "CREDIT",
                                  "amount": 100.00,
                                  "currency": "USD"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages[0]").value(containsString("eventTimestamp")));
    }

    @Test
    void submitEvent_missingCurrency_returns400() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "evt-bad",
                                  "accountId": "acct-1",
                                  "type": "CREDIT",
                                  "amount": 100.00,
                                  "eventTimestamp": "2026-05-15T10:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messages[0]").value(containsString("currency")));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private void postEvent(String eventId, String accountId, String type, String amount, String timestamp)
            throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson(eventId, accountId, type, amount, timestamp)))
                .andExpect(status().isCreated());
    }

    private String eventJson(String eventId, String accountId, String type, String amount, String timestamp) {
        return """
                {
                  "eventId": "%s",
                  "accountId": "%s",
                  "type": "%s",
                  "amount": %s,
                  "currency": "USD",
                  "eventTimestamp": "%s"
                }
                """.formatted(eventId, accountId, type, amount, timestamp);
    }
}
