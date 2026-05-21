package com.eventledger.service;

import com.eventledger.exception.EventNotFoundException;
import com.eventledger.model.*;
import com.eventledger.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventService eventService;

    private EventRequest request;
    private Event savedEvent;

    @BeforeEach
    void setUp() {
        request = new EventRequest();
        request.setEventId("evt-001");
        request.setAccountId("acct-123");
        request.setType("CREDIT");
        request.setAmount(new BigDecimal("100.00"));
        request.setCurrency("USD");
        request.setEventTimestamp(Instant.parse("2026-05-15T10:00:00Z"));

        savedEvent = new Event();
        savedEvent.setEventId("evt-001");
        savedEvent.setAccountId("acct-123");
        savedEvent.setType("CREDIT");
        savedEvent.setAmount(new BigDecimal("100.00"));
        savedEvent.setCurrency("USD");
        savedEvent.setEventTimestamp(Instant.parse("2026-05-15T10:00:00Z"));
    }

    // ── submitEvent ───────────────────────────────────────────────────────────────

    @Test
    void submitEvent_newEvent_returnsCreatedTrue() {
        when(eventRepository.save(any(Event.class))).thenReturn(savedEvent);

        EventResult result = eventService.submitEvent(request);

        assertThat(result.created()).isTrue();
        assertThat(result.event().getEventId()).isEqualTo("evt-001");
    }

    @Test
    void submitEvent_duplicate_returnsCreatedFalse() {
        when(eventRepository.save(any(Event.class))).thenThrow(new DataIntegrityViolationException("dup"));
        when(eventRepository.findById("evt-001")).thenReturn(Optional.of(savedEvent));

        EventResult result = eventService.submitEvent(request);

        assertThat(result.created()).isFalse();
        assertThat(result.event().getEventId()).isEqualTo("evt-001");
    }

    @Test
    void submitEvent_duplicateButFindByIdEmpty_throwsIllegalState() {
        when(eventRepository.save(any(Event.class))).thenThrow(new DataIntegrityViolationException("dup"));
        when(eventRepository.findById("evt-001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.submitEvent(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("evt-001");
    }

    @Test
    void submitEvent_mapsAllFieldsCorrectly() {
        when(eventRepository.save(any(Event.class))).thenReturn(savedEvent);
        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);

        eventService.submitEvent(request);

        verify(eventRepository).save(captor.capture());
        Event mapped = captor.getValue();
        assertThat(mapped.getEventId()).isEqualTo("evt-001");
        assertThat(mapped.getAccountId()).isEqualTo("acct-123");
        assertThat(mapped.getType()).isEqualTo("CREDIT");
        assertThat(mapped.getAmount()).isEqualByComparingTo("100.00");
        assertThat(mapped.getCurrency()).isEqualTo("USD");
        assertThat(mapped.getEventTimestamp()).isEqualTo(Instant.parse("2026-05-15T10:00:00Z"));
    }

    @Test
    void submitEvent_nullMetadata_preservedInMapping() {
        request.setMetadata(null);
        when(eventRepository.save(any(Event.class))).thenReturn(savedEvent);
        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);

        eventService.submitEvent(request);

        verify(eventRepository).save(captor.capture());
        assertThat(captor.getValue().getMetadata()).isNull();
    }

    @Test
    void submitEvent_withMetadata_preservedInMapping() {
        request.setMetadata(Map.of("source", "batch", "batchId", "B-001"));
        when(eventRepository.save(any(Event.class))).thenReturn(savedEvent);
        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);

        eventService.submitEvent(request);

        verify(eventRepository).save(captor.capture());
        assertThat(captor.getValue().getMetadata()).containsEntry("source", "batch");
    }

    // ── getEvent ─────────────────────────────────────────────────────────────────

    @Test
    void getEvent_found_returnsEvent() {
        when(eventRepository.findById("evt-001")).thenReturn(Optional.of(savedEvent));

        Event result = eventService.getEvent("evt-001");

        assertThat(result.getEventId()).isEqualTo("evt-001");
    }

    @Test
    void getEvent_notFound_throwsEventNotFoundException() {
        when(eventRepository.findById("evt-missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.getEvent("evt-missing"))
                .isInstanceOf(EventNotFoundException.class)
                .hasMessageContaining("evt-missing");
    }

    // ── listByAccount ─────────────────────────────────────────────────────────────

    @Test
    void listByAccount_returnsWrappedPagedResponse() {
        PageImpl<Event> page = new PageImpl<>(List.of(savedEvent));
        when(eventRepository.findByAccountId(eq("acct-123"), any(Pageable.class))).thenReturn(page);

        PagedResponse<Event> response = eventService.listByAccount("acct-123", 0, 20);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getTotalElements()).isEqualTo(1);
    }

    @Test
    void listByAccount_usesAscendingTimestampSort() {
        PageImpl<Event> page = new PageImpl<>(List.of());
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        when(eventRepository.findByAccountId(eq("acct-123"), captor.capture())).thenReturn(page);

        eventService.listByAccount("acct-123", 0, 10);

        Sort.Order order = captor.getValue().getSort().getOrderFor("eventTimestamp");
        assertThat(order).isNotNull();
        assertThat(order.isAscending()).isTrue();
    }

    @Test
    void listByAccount_passesPageAndSizeCorrectly() {
        PageImpl<Event> page = new PageImpl<>(List.of());
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        when(eventRepository.findByAccountId(eq("acct-123"), captor.capture())).thenReturn(page);

        eventService.listByAccount("acct-123", 2, 5);

        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }

    // ── getBalance ────────────────────────────────────────────────────────────────

    @Test
    void getBalance_returnsComputedBalance() {
        when(eventRepository.computeBalance("acct-123")).thenReturn(new BigDecimal("250.00"));
        when(eventRepository.findCurrenciesByAccountId("acct-123")).thenReturn(List.of("USD"));

        BalanceResponse response = eventService.getBalance("acct-123");

        assertThat(response.getBalance()).isEqualByComparingTo("250.00");
        assertThat(response.getCurrency()).isEqualTo("USD");
        assertThat(response.getAccountId()).isEqualTo("acct-123");
    }

    @Test
    void getBalance_noEvents_returnsZeroWithUsdDefault() {
        when(eventRepository.computeBalance("acct-empty")).thenReturn(BigDecimal.ZERO);
        when(eventRepository.findCurrenciesByAccountId("acct-empty")).thenReturn(List.of());

        BalanceResponse response = eventService.getBalance("acct-empty");

        assertThat(response.getBalance()).isEqualByComparingTo("0");
        assertThat(response.getCurrency()).isEqualTo("USD");
    }

    @Test
    void getBalance_multipleCurrencies_usesFirst() {
        when(eventRepository.computeBalance("acct-123")).thenReturn(new BigDecimal("100.00"));
        when(eventRepository.findCurrenciesByAccountId("acct-123")).thenReturn(List.of("EUR", "USD"));

        BalanceResponse response = eventService.getBalance("acct-123");

        assertThat(response.getCurrency()).isEqualTo("EUR");
    }
}
