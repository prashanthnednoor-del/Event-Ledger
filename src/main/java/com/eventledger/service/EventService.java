package com.eventledger.service;

import com.eventledger.exception.EventNotFoundException;
import com.eventledger.model.BalanceResponse;
import com.eventledger.model.Event;
import com.eventledger.model.EventRequest;
import com.eventledger.model.EventResult;
import com.eventledger.model.PagedResponse;
import com.eventledger.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public EventResult submitEvent(EventRequest request) {
        Event event = mapToEntity(request);
        try {
            Event saved = eventRepository.save(event);
            log.info("Event created: eventId={}, accountId={}, type={}, amount={}",
                    saved.getEventId(), saved.getAccountId(), saved.getType(), saved.getAmount());
            return new EventResult(saved, true);
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate eventId received, returning original: eventId={}", request.getEventId());
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
        log.debug("Computing balance for accountId={}", accountId);
        BigDecimal balance = eventRepository.computeBalance(accountId);
        List<String> currencies = eventRepository.findCurrenciesByAccountId(accountId);
        String currency = currencies.isEmpty() ? "USD" : currencies.get(0);
        log.debug("Balance result for accountId={}: {} {}", accountId, balance, currency);
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
