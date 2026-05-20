package com.eventledger.service;

import com.eventledger.model.Event;
import com.eventledger.model.EventRequest;
import com.eventledger.model.EventResult;
import com.eventledger.repository.EventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

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
