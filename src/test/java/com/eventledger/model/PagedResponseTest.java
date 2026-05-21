package com.eventledger.model;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class PagedResponseTest {

    @Test
    void emptyPage_allFieldsReflectEmpty() {
        PagedResponse<String> response = new PagedResponse<>(
                new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
        assertThat(response.getTotalPages()).isZero();
        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(20);
    }

    @Test
    void singleItem_correctMetadata() {
        PagedResponse<String> response = new PagedResponse<>(
                new PageImpl<>(List.of("item"), PageRequest.of(0, 20), 1));

        assertThat(response.getContent()).containsExactly("item");
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getTotalPages()).isEqualTo(1);
        assertThat(response.getPage()).isZero();
    }

    @Test
    void partialLastPage_totalPagesRoundsUp() {
        // 5 items, page size 2 → 3 pages (2 + 2 + 1)
        PagedResponse<Integer> response = new PagedResponse<>(
                new PageImpl<>(List.of(5), PageRequest.of(2, 2), 5));

        assertThat(response.getTotalPages()).isEqualTo(3);
        assertThat(response.getTotalElements()).isEqualTo(5);
        assertThat(response.getPage()).isEqualTo(2);
        assertThat(response.getSize()).isEqualTo(2);
    }
}
