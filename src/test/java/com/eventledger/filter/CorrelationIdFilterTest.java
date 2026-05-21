package com.eventledger.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain chain;

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        MDC.remove("correlationId");
    }

    @Test
    void existingHeader_usedAsCorrelationId() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn("my-trace-123");

        filter.doFilterInternal(request, response, chain);

        verify(response).setHeader(CorrelationIdFilter.HEADER, "my-trace-123");
        verify(chain).doFilter(request, response);
    }

    @Test
    void missingHeader_generatesUUID() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(response).setHeader(eq(CorrelationIdFilter.HEADER), argThat(id ->
                id != null && id.matches(
                        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")));
    }

    @Test
    void blankHeader_generatesUUID() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn("   ");

        filter.doFilterInternal(request, response, chain);

        verify(response).setHeader(eq(CorrelationIdFilter.HEADER), argThat(id ->
                id != null && !id.isBlank() && !id.equals("   ")));
    }

    @Test
    void mdcClearedAfterRequest() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn("trace-xyz");

        filter.doFilterInternal(request, response, chain);

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void mdcClearedEvenWhenChainThrows() throws Exception {
        when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn("trace-err");
        doThrow(new ServletException("downstream failure")).when(chain).doFilter(request, response);

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, chain))
                .isInstanceOf(ServletException.class);

        assertThat(MDC.get("correlationId")).isNull();
    }
}
