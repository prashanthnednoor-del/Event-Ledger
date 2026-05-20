package com.eventledger.controller;

import com.eventledger.exception.ErrorResponse;
import com.eventledger.model.Event;
import com.eventledger.model.EventRequest;
import com.eventledger.model.EventResult;
import com.eventledger.model.PagedResponse;
import com.eventledger.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Events", description = "Submit and retrieve transaction events")
@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @Operation(
        summary = "Submit a transaction event",
        description = "Submits a new event. If the eventId already exists the original event is returned unchanged — no duplicate is created and the balance is not affected.",
        responses = {
            @ApiResponse(responseCode = "201", description = "Event created"),
            @ApiResponse(responseCode = "200", description = "Duplicate eventId — original event returned"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
        }
    )
    @PostMapping
    public ResponseEntity<Event> submitEvent(@Valid @RequestBody EventRequest request) {
        EventResult result = eventService.submitEvent(request);
        return result.created()
                ? ResponseEntity.status(HttpStatus.CREATED).body(result.event())
                : ResponseEntity.ok(result.event());
    }

    @Operation(
        summary = "Retrieve a single event by ID",
        responses = {
            @ApiResponse(responseCode = "200", description = "Event found"),
            @ApiResponse(responseCode = "404", description = "Event not found",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
        }
    )
    @GetMapping("/{id}")
    public ResponseEntity<Event> getEvent(
            @Parameter(description = "The eventId to retrieve", example = "evt-001")
            @PathVariable String id) {
        return ResponseEntity.ok(eventService.getEvent(id));
    }

    @Operation(
        summary = "List events for an account",
        description = "Returns events in chronological order by eventTimestamp regardless of arrival order. Supports pagination via page and size parameters.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Paginated event list")
        }
    )
    @GetMapping
    public ResponseEntity<PagedResponse<Event>> listEvents(
            @Parameter(description = "Account ID to filter by", example = "acct-123", required = true)
            @RequestParam String account,
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of events per page", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(eventService.listByAccount(account, page, size));
    }
}
