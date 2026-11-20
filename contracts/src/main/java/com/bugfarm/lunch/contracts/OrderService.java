package com.bugfarm.lunch.contracts;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface OrderService {
    /** Orders for the date, sorted by displayName. */
    List<Order> ordersFor(LocalDate date);

    /** Deletes the order; false if not found. */
    boolean deleteOrder(UUID id);
}
