package com.eventledger.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class MapToJsonConverterTest {

    private MapToJsonConverter converter;

    @BeforeEach
    void setUp() {
        converter = new MapToJsonConverter();
    }

    // ── toDatabase ────────────────────────────────────────────────────────────────

    @Test
    void toDatabase_null_returnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void toDatabase_emptyMap_returnsEmptyJson() {
        assertThat(converter.convertToDatabaseColumn(Map.of())).isEqualTo("{}");
    }

    @Test
    void toDatabase_populatedMap_returnsJsonString() {
        String json = converter.convertToDatabaseColumn(Map.of("key", "value"));
        assertThat(json).contains("\"key\"").contains("\"value\"");
    }

    @Test
    void toDatabase_nestedMap_serialisesCorrectly() {
        Map<String, Object> nested = Map.of("outer", Map.of("inner", "val"));
        String json = converter.convertToDatabaseColumn(nested);
        assertThat(json).contains("inner").contains("val");
    }

    // ── fromDatabase ──────────────────────────────────────────────────────────────

    @Test
    void fromDatabase_null_returnsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void fromDatabase_validJson_returnsMap() {
        Map<String, Object> result = converter.convertToEntityAttribute("{\"source\":\"batch\"}");
        assertThat(result).containsEntry("source", "batch");
    }

    @Test
    void fromDatabase_malformedJson_throwsIllegalState() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("{not-valid-json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("metadata");
    }

    @Test
    void roundtrip_mapToJsonToMap_isIdentical() {
        Map<String, Object> original = Map.of("batchId", "B-001", "count", 42);
        String json = converter.convertToDatabaseColumn(original);
        Map<String, Object> restored = converter.convertToEntityAttribute(json);
        assertThat(restored).containsAllEntriesOf(Map.of("batchId", "B-001"));
    }

    @Test
    void fromDatabase_jsonWithList_restoresCorrectly() {
        String json = "{\"tags\":[\"a\",\"b\"]}";
        Map<String, Object> result = converter.convertToEntityAttribute(json);
        assertThat(result.get("tags")).isInstanceOf(List.class);
    }
}
