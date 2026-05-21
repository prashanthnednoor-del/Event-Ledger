package com.eventledger.model;

import com.fasterxml.jackson.core.JsonGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BigDecimalScaleSerializerTest {

    @Mock
    private JsonGenerator jsonGenerator;

    private BigDecimalScaleSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new BigDecimalScaleSerializer();
    }

    private BigDecimal captured() throws Exception {
        ArgumentCaptor<BigDecimal> captor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(jsonGenerator).writeNumber(captor.capture());
        return captor.getValue();
    }

    @Test
    void wholeNumber_serializedWithTwoDecimals() throws Exception {
        serializer.serialize(new BigDecimal("150"), jsonGenerator, null);
        assertThat(captured().toPlainString()).isEqualTo("150.00");
    }

    @Test
    void oneDecimal_paddedToTwo() throws Exception {
        serializer.serialize(new BigDecimal("150.1"), jsonGenerator, null);
        assertThat(captured().toPlainString()).isEqualTo("150.10");
    }

    @Test
    void halfUpRoundingUp() throws Exception {
        serializer.serialize(new BigDecimal("150.126"), jsonGenerator, null);
        assertThat(captured().toPlainString()).isEqualTo("150.13");
    }

    @Test
    void halfUpRoundingDown() throws Exception {
        serializer.serialize(new BigDecimal("150.124"), jsonGenerator, null);
        assertThat(captured().toPlainString()).isEqualTo("150.12");
    }

    @Test
    void exactlyHalf_roundsUp() throws Exception {
        serializer.serialize(new BigDecimal("150.125"), jsonGenerator, null);
        assertThat(captured().toPlainString()).isEqualTo("150.13");
    }

    @Test
    void fourDecimalInput_roundedToTwo() throws Exception {
        serializer.serialize(new BigDecimal("150.0000"), jsonGenerator, null);
        assertThat(captured().toPlainString()).isEqualTo("150.00");
    }
}
