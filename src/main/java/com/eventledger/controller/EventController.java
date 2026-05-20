package com.eventledger.controller;

import com.eventledger.model.Event;
import com.eventledger.model.EventRequest;
import com.eventledger.model.EventResult;
import com.eventledger.service.EventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<Event> submitEvent(@Valid @RequestBody EventRequest request) {
        EventResult result = eventService.submitEvent(request);
        return result.created()
                ? ResponseEntity.status(HttpStatus.CREATED).body(result.event())
                : ResponseEntity.ok(result.event());
    }
}
