package com.bugfarm.lunch.orders;

import com.bugfarm.lunch.contracts.Order;
import com.bugfarm.lunch.contracts.OrderService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores lunch orders: at most one per Teams user per day. */
@Service
public class JdbcOrderService implements OrderService {

    private static final String COLUMNS =
            "id, teams_user_id, display_name, food_type, item, notes, order_date, placed_at";

    private final JdbcClient jdbc;

    public JdbcOrderService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserts the order, replacing any order the same user already has on that date. */
    @Transactional
    public Order save(String teamsUserId, String displayName, String foodType, String item,
                      String notes, LocalDate date, Instant placedAt) {
        cancel(teamsUserId, date);
        Order order = new Order(UUID.randomUUID(), teamsUserId, displayName, foodType, item,
                notes, date, placedAt);
        jdbc.sql("INSERT INTO orders (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                .params(order.id(), order.teamsUserId(), order.displayName(), order.foodType(),
                        order.item(), order.notes(), order.orderDate(),
                        OffsetDateTime.ofInstant(order.placedAt(), ZoneOffset.UTC))
                .update();
        return order;
    }

    public Optional<Order> findFor(String teamsUserId, LocalDate date) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM orders WHERE teams_user_id = ? AND order_date = ?")
                .params(teamsUserId, date)
                .query(JdbcOrderService::map)
                .optional();
    }

    /** Removes the user's order for the date; false if there was none. */
    public boolean cancel(String teamsUserId, LocalDate date) {
        return jdbc.sql("DELETE FROM orders WHERE teams_user_id = ? AND order_date = ?")
                .params(teamsUserId, date)
                .update() > 0;
    }

    @Override
    public List<Order> ordersFor(LocalDate date) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM orders WHERE order_date = ? ORDER BY display_name, id")
                .param(date)
                .query(JdbcOrderService::map)
                .list();
    }

    @Override
    public boolean deleteOrder(UUID id) {
        return jdbc.sql("DELETE FROM orders WHERE id = ?").param(id).update() > 0;
    }

    private static Order map(ResultSet rs, int rowNum) throws SQLException {
        return new Order(
                rs.getObject("id", UUID.class),
                rs.getString("teams_user_id"),
                rs.getString("display_name"),
                rs.getString("food_type"),
                rs.getString("item"),
                rs.getString("notes"),
                rs.getObject("order_date", LocalDate.class),
                rs.getObject("placed_at", OffsetDateTime.class).toInstant());
    }
}
