package com.bugfarm.lunch.contracts;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A placed lunch order. {@code notes} is {@code ""} when there are none. */
public record Order(UUID id, String teamsUserId, String displayName, String foodType,
                    String item, String notes, LocalDate orderDate, Instant placedAt) {

    public Order {
        notes = notes == null ? "" : notes;
    }
}
