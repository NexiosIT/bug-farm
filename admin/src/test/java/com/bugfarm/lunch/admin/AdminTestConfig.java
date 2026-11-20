package com.bugfarm.lunch.admin;

import com.bugfarm.lunch.contracts.Order;
import com.bugfarm.lunch.contracts.OrderService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Beans the admin module gets from elsewhere at runtime: a fixed {@link Clock}
 * (normally from app) and an in-memory {@link OrderService} (normally from orders).
 */
@TestConfiguration
public class AdminTestConfig {

    public static final UUID JAN_ORDER_ID = UUID.fromString("3f2b8c1e-5a47-4d0e-9b1a-7c6d2e8f4a10");

    /** Wednesday 2026-11-18, 09:00 in Brussels (UTC+1). */
    @Bean
    public Clock clock() {
        return Clock.fixed(Instant.parse("2026-11-18T08:00:00Z"), ZoneId.of("Europe/Brussels"));
    }

    @Bean
    public OrderService orderService() {
        return new InMemoryOrderService(List.of(new Order(JAN_ORDER_ID, "aad-jan", "Jan Peeters", "Sandwich",
                "Ham & cheese", "no butter", LocalDate.of(2026, 11, 18), Instant.parse("2026-11-18T07:45:00Z"))));
    }

    public static class InMemoryOrderService implements OrderService {

        private final List<Order> orders;

        public InMemoryOrderService(List<Order> orders) {
            this.orders = new CopyOnWriteArrayList<>(orders);
        }

        @Override
        public List<Order> ordersFor(LocalDate date) {
            return orders.stream().filter(o -> o.orderDate().equals(date)).toList();
        }

        @Override
        public boolean deleteOrder(UUID id) {
            return orders.removeIf(o -> o.id().equals(id));
        }
    }
}
