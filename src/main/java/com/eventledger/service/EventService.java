package com.eventledger.service;

import com.eventledger.exception.EventNotFoundException;
import com.eventledger.model.BalanceResponse;
import com.eventledger.model.Event;
import com.eventledger.model.EventRequest;
import com.eventledger.model.EventResult;
import com.eventledger.model.PagedResponse;
import com.eventledger.repository.EventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public EventResult submitEvent(EventRequest request) {
        Event event = mapToEntity(request);
        try {
            return new EventResult(eventRepository.save(event), true);
        } catch (DataIntegrityViolationException e) {
            // Duplicate eventId — race-safe: DB constraint caught it, return the original
            Event existing = eventRepository.findById(request.getEventId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Event not found after duplicate key violation: " + request.getEventId()));
            return new EventResult(existing, false);
        }
    }

    public Event getEvent(String eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    public PagedResponse<Event> listByAccount(String accountId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("eventTimestamp").ascending());
        return new PagedResponse<>(eventRepository.findByAccountId(accountId, pageable));
    }

    public BalanceResponse getBalance(String accountId) {
        BigDecimal balance = eventRepository.computeBalance(accountId);
        List<String> currencies = eventRepository.findCurrenciesByAccountId(accountId);
        String currency = currencies.isEmpty() ? "USD" : currencies.get(0);
        return new BalanceResponse(accountId, balance, currency);
    }

    private Event mapToEntity(EventRequest request) {
        Event event = new Event();
        event.setEventId(request.getEventId());
        event.setAccountId(request.getAccountId());
        event.setType(request.getType());
        event.setAmount(request.getAmount());
        event.setCurrency(request.getCurrency());
        event.setEventTimestamp(request.getEventTimestamp());
        event.setMetadata(request.getMetadata());
        return event;
    }
}
