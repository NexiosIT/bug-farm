package com.bugfarm.lunch.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderTest {

    @Test
    void nullNotesBecomeEmpty() {
        Order o = new Order(UUID.randomUUID(), "u1", "Jan", "Sandwich", "Tuna", null,
                LocalDate.of(2026, 11, 20), Instant.parse("2026-11-20T08:00:00Z"));
        assertThat(o.notes()).isEqualTo("");
    }

    @Test
    void notesAreKept() {
        Order o = new Order(UUID.randomUUID(), "u1", "Jan", "Sandwich", "Tuna", "no butter",
                LocalDate.of(2026, 11, 20), Instant.parse("2026-11-20T08:00:00Z"));
        assertThat(o.notes()).isEqualTo("no butter");
    }
}
