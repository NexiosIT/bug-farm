package com.bugfarm.lunch.orders.suggest;

import com.bugfarm.lunch.contracts.MenuItem;
import java.time.Instant;
import java.util.List;

/** The numbered menu items offered to one person, with the notes from their message. */
public record OpenChoice(List<MenuItem> options, String notes, Instant offeredAt) {
    public OpenChoice {
        options = List.copyOf(options);
        notes = notes == null ? "" : notes;
    }
}
