package com.bugfarm.lunch.admin.web;

import com.bugfarm.lunch.contracts.Order;
import com.bugfarm.lunch.contracts.OrderService;
import com.bugfarm.lunch.contracts.OrderingWindow;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
public class OrdersController {

    public record OrderView(UUID id, String displayName, String foodType, String item, String notes,
                            Instant placedAt) {
        static OrderView of(Order o) {
            return new OrderView(o.id(), o.displayName(), o.foodType(), o.item(), o.notes(), o.placedAt());
        }
    }

    public record TodayOrders(LocalDate date, boolean open, String cutOff, List<OrderView> orders) {}

    private final OrderService orders;
    private final OrderingWindow window;
    private final Clock clock;

    public OrdersController(OrderService orders, OrderingWindow window, Clock clock) {
        this.orders = orders;
        this.window = window;
        this.clock = clock;
    }

    @GetMapping("/today")
    public TodayOrders today() {
        LocalDate today = LocalDate.now(clock);
        List<OrderView> views = orders.ordersFor(today).stream().map(OrderView::of).toList();
        return new TodayOrders(today, window.isOpen(clock.instant()), TimeFormat.hhmm(window.cutOff()), views);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        return orders.deleteOrder(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
