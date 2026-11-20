package com.bugfarm.lunch.admin.email;

import static org.assertj.core.api.Assertions.assertThat;

import com.bugfarm.lunch.contracts.Order;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmailDraftBuilderTest {

    private static final LocalDate FRI_20_NOV = LocalDate.of(2026, 11, 20);

    private final EmailDraftBuilder builder = new EmailDraftBuilder();

    private static Order order(String name, String foodType, String item, String notes) {
        return new Order(UUID.randomUUID(), "aad-" + name, name, foodType, item, notes, FRI_20_NOV,
                Instant.parse("2026-11-20T08:00:00Z"));
    }

    /** The five orders behind the spec's example: 3 × Ham & cheese, 2 × Tuna. Deliberately unsorted. */
    private static final List<Order> FIVE = List.of(
            order("Lisa Maes", "Sandwich", "Tuna", ""),
            order("Jan Peeters", "Sandwich", "Ham & cheese", "no butter"),
            order("Eva Wouters", "Sandwich", "Ham & cheese", ""),
            order("Bram Jansen", "Sandwich", "Tuna", ""),
            order("An Claes", "Sandwich", "Ham & cheese", ""));

    @Test
    void subjectAndSummaryMatchSpecExample() {
        EmailDraft draft = builder.build(FIVE, FRI_20_NOV, "orders@shop.example", false);

        assertThat(draft.subject()).isEqualTo("Order Lunch, Fri 20 Nov");
        assertThat(draft.body()).isEqualTo("""
                Order for Fri 20 Nov

                3 × Ham & cheese
                2 × Tuna

                Per person:
                An Claes: Ham & cheese
                Bram Jansen: Tuna
                Eva Wouters: Ham & cheese
                Jan Peeters: Ham & cheese (no butter)
                Lisa Maes: Tuna""");
        assertThat(draft.finalDraft()).isFalse();
    }

    @Test
    void foodTypeShownOnlyWhenSeveral() {
        List<Order> mixed = List.of(
                order("Jan Peeters", "Sandwich", "Tuna", ""),
                order("Lisa Maes", "Soup", "Tomato", ""),
                order("An Claes", "Soup", "Tomato", ""));

        String body = builder.build(mixed, FRI_20_NOV, "", true).body();

        assertThat(body).contains("2 × Tomato (Soup)\n1 × Tuna (Sandwich)\n");
        assertThat(builder.build(FIVE, FRI_20_NOV, "", true).body()).doesNotContain("(Sandwich)");
    }

    @Test
    void sameCountSortedByName() {
        List<Order> tie = List.of(
                order("Jan Peeters", "Sandwich", "Tuna", ""),
                order("Lisa Maes", "Sandwich", "Chicken curry", ""));

        assertThat(builder.build(tie, FRI_20_NOV, "", false).body())
                .contains("1 × Chicken curry\n1 × Tuna\n");
    }

    @Test
    void noOrdersBody() {
        EmailDraft draft = builder.build(List.of(), FRI_20_NOV, "orders@shop.example", true);

        assertThat(draft.body()).isEqualTo("Order for Fri 20 Nov\n\nNo orders today.");
        assertThat(draft.subject()).isEqualTo("Order Lunch, Fri 20 Nov");
    }

    @Test
    void deletedMenuItemStillListed() {
        // "Salami" was on the menu when ordered and has since been removed: the saved text is used as is.
        List<Order> orders = List.of(order("Jan Peeters", "Sandwich", "Salami", "extra pickles"));

        String body = builder.build(orders, FRI_20_NOV, "", true).body();

        assertThat(body).contains("1 × Salami\n");
        assertThat(body).endsWith("Per person:\nJan Peeters: Salami (extra pickles)");
    }

    @Test
    void toIsShopEmail() {
        assertThat(builder.build(FIVE, FRI_20_NOV, "orders@shop.example", true).to())
                .isEqualTo("orders@shop.example");
        assertThat(builder.build(FIVE, FRI_20_NOV, "orders@shop.example", true).finalDraft()).isTrue();
    }
}
