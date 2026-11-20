package com.bugfarm.lunch.orders;

import static org.assertj.core.api.Assertions.assertThat;

import com.bugfarm.lunch.contracts.Order;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@JdbcTest
@Import(JdbcOrderService.class)
class JdbcOrderServiceTest {

    private static final LocalDate FRI = LocalDate.of(2026, 11, 20);
    private static final LocalDate THU = LocalDate.of(2026, 11, 19);
    private static final Instant AT = Instant.parse("2026-11-20T08:15:00Z");

    @Autowired JdbcOrderService service;
    @Autowired JdbcClient jdbc;

    private int rowCount() {
        return jdbc.sql("SELECT COUNT(*) FROM orders").query(Integer.class).single();
    }

    @Test
    void saveThenFindReturnsOrder() {
        Order saved = service.save("aad-jan", "Jan Peeters", "Sandwich", "Ham & cheese", "no butter", FRI, AT);

        Order found = service.findFor("aad-jan", FRI).orElseThrow();

        assertThat(found.id()).isEqualTo(saved.id());
        assertThat(found.teamsUserId()).isEqualTo("aad-jan");
        assertThat(found.displayName()).isEqualTo("Jan Peeters");
        assertThat(found.foodType()).isEqualTo("Sandwich");
        assertThat(found.item()).isEqualTo("Ham & cheese");
        assertThat(found.notes()).isEqualTo("no butter");
        assertThat(found.orderDate()).isEqualTo(FRI);
        assertThat(found.placedAt()).isEqualTo(AT);
    }

    @Test
    void secondSaveSameDayReplacesFirst() {
        service.save("aad-jan", "Jan Peeters", "Sandwich", "Ham & cheese", "", FRI, AT);
        service.save("aad-jan", "Jan Peeters", "Sandwich", "Tuna", "", FRI, AT.plusSeconds(60));

        assertThat(rowCount()).isEqualTo(1);
        assertThat(service.findFor("aad-jan", FRI).orElseThrow().item()).isEqualTo("Tuna");
    }

    @Test
    void cancelRemovesOnlyThatDay() {
        service.save("aad-jan", "Jan Peeters", "Sandwich", "Tuna", "", THU, AT.minusSeconds(86_400));
        service.save("aad-jan", "Jan Peeters", "Sandwich", "Ham & cheese", "", FRI, AT);

        assertThat(service.cancel("aad-jan", FRI)).isTrue();

        assertThat(service.findFor("aad-jan", FRI)).isEmpty();
        assertThat(service.findFor("aad-jan", THU)).isPresent();
        assertThat(service.cancel("aad-jan", FRI)).isFalse();
    }

    @Test
    void ordersForSortedByDisplayName() {
        service.save("aad-lisa", "Lisa Maes", "Sandwich", "Tuna", "", FRI, AT);
        service.save("aad-an", "An de Wit", "Sandwich", "Tuna", "", FRI, AT);
        service.save("aad-jan", "Jan Peeters", "Sandwich", "Tuna", "", FRI, AT);
        service.save("aad-old", "Bob Old", "Sandwich", "Tuna", "", THU, AT);

        assertThat(service.ordersFor(FRI)).extracting(Order::displayName)
                .containsExactly("An de Wit", "Jan Peeters", "Lisa Maes");
    }

    @Test
    void deleteOrderUnknownIdReturnsFalse() {
        Order saved = service.save("aad-jan", "Jan Peeters", "Sandwich", "Tuna", "", FRI, AT);

        assertThat(service.deleteOrder(UUID.randomUUID())).isFalse();
        assertThat(service.deleteOrder(saved.id())).isTrue();
        assertThat(rowCount()).isZero();
    }

    @Test
    void cancelThenOrderAgainLeavesOneRow() {
        service.save("aad-jan", "Jan Peeters", "Sandwich", "Tuna", "", FRI, AT);
        service.cancel("aad-jan", FRI);
        service.save("aad-jan", "Jan Peeters", "Sandwich", "Ham & cheese", "", FRI, AT.plusSeconds(30));

        assertThat(rowCount()).isEqualTo(1);
        assertThat(service.ordersFor(FRI)).extracting(Order::item).containsExactly("Ham & cheese");
    }
}
