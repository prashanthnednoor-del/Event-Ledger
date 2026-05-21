package com.eventledger.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class EventNotFoundExceptionTest {

    @Test
    void messageIncludesEventId() {
        EventNotFoundException ex = new EventNotFoundException("evt-abc");
        assertThat(ex.getMessage()).isEqualTo("Event not found: evt-abc");
    }

    @Test
    void isRuntimeException() {
        assertThat(new EventNotFoundException("x")).isInstanceOf(RuntimeException.class);
    }
}
