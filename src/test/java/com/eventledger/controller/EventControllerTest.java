package com.eventledger.controller;

import com.eventledger.model.*;
import com.eventledger.service.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventService eventService;

    private Event sampleEvent() {
        Event e = new Event();
        e.setEventId("evt-001");
        e.setAccountId("acct-123");
        e.setType("CREDIT");
        e.setAmount(new BigDecimal("100.00"));
        e.setCurrency("USD");
        e.setEventTimestamp(Instant.parse("2026-05-15T10:00:00Z"));
        return e;
    }

    private String eventJson() {
        return """
                {
                  "eventId": "evt-001",
                  "accountId": "acct-123",
                  "type": "CREDIT",
                  "amount": 100.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T10:00:00Z"
                }
                """;
    }

    // ── submitEvent ───────────────────────────────────────────────────────────────

    @Test
    void submitEvent_newEvent_returns201() throws Exception {
        when(eventService.submitEvent(any())).thenReturn(new EventResult(sampleEvent(), true));

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value("evt-001"));
    }

    @Test
    void submitEvent_duplicate_returns200() throws Exception {
        when(eventService.submitEvent(any())).thenReturn(new EventResult(sampleEvent(), false));

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt-001"));
    }

    // ── getEvent ──────────────────────────────────────────────────────────────────

    @Test
    void getEvent_found_returns200() throws Exception {
        when(eventService.getEvent("evt-001")).thenReturn(sampleEvent());

        mockMvc.perform(get("/events/evt-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt-001"));
    }

    // ── listEvents ────────────────────────────────────────────────────────────────

    @Test
    void listEvents_returns200WithPagedResponse() throws Exception {
        PagedResponse<Event> paged = new PagedResponse<>(
                new PageImpl<>(List.of(sampleEvent()), PageRequest.of(0, 20), 1));
        when(eventService.listByAccount(eq("acct-123"), eq(0), eq(20))).thenReturn(paged);

        mockMvc.perform(get("/events").param("account", "acct-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventId").value("evt-001"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listEvents_defaultPageAndSize_passedToService() throws Exception {
        PagedResponse<Event> paged = new PagedResponse<>(
                new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(eventService.listByAccount(eq("acct-123"), eq(0), eq(20))).thenReturn(paged);

        mockMvc.perform(get("/events").param("account", "acct-123"))
                .andExpect(status().isOk());

        verify(eventService).listByAccount("acct-123", 0, 20);
    }

    @Test
    void listEvents_customPageAndSize_passedToService() throws Exception {
        PagedResponse<Event> paged = new PagedResponse<>(
                new PageImpl<>(List.of(), PageRequest.of(1, 5), 0));
        when(eventService.listByAccount(eq("acct-123"), eq(1), eq(5))).thenReturn(paged);

        mockMvc.perform(get("/events")
                        .param("account", "acct-123")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk());

        verify(eventService).listByAccount("acct-123", 1, 5);
    }
}
