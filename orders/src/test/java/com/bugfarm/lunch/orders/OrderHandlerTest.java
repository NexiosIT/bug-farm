package com.bugfarm.lunch.orders;

import static org.assertj.core.api.Assertions.assertThat;

import com.bugfarm.lunch.contracts.Intent;
import com.bugfarm.lunch.contracts.Interpretation;
import com.bugfarm.lunch.contracts.Order;
import com.bugfarm.lunch.orders.fakes.FakeOrderInterpreter;
import com.bugfarm.lunch.orders.fakes.FakeOrderingWindow;
import com.bugfarm.lunch.orders.fakes.FakesConfig;
import com.bugfarm.lunch.orders.parse.CommandParser;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;

@JdbcTest
@Import({FakesConfig.class, JdbcOrderService.class, CommandParser.class, OrderHandler.class})
class OrderHandlerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);

    @Autowired OrderHandler handler;
    @Autowired JdbcOrderService orders;
    @Autowired FakeOrderInterpreter ai;
    @Autowired FakeOrderingWindow window;

    @BeforeEach
    void reset() {
        window.setOpen(true);
        ai.received().clear();
        ai.willReturn(Interpretation.unclear("preset not set"));
    }

    private List<Order> saved() {
        return orders.ordersFor(TODAY);
    }

    @Test
    void strictOrderSavesWithMenuSpelling() {
        String reply = handler.handle("aad-jan", "Jan", "sandwich: HAM & CHEESE, no butter");

        assertThat(reply).isEqualTo("Got it, Jan: Sandwich, Ham & cheese (no butter)");
        assertThat(saved()).singleElement().satisfies(o -> {
            assertThat(o.teamsUserId()).isEqualTo("aad-jan");
            assertThat(o.foodType()).isEqualTo("Sandwich");
            assertThat(o.item()).isEqualTo("Ham & cheese");
            assertThat(o.notes()).isEqualTo("no butter");
            assertThat(o.placedAt()).isEqualTo(FakesConfig.NOW);
        });
    }

    @Test
    void confirmationWithoutNotesHasNoBrackets() {
        assertThat(handler.handle("aad-jan", "Jan", "sandwich: tuna")).isEqualTo("Got it, Jan: Sandwich, Tuna");
    }

    @Test
    void strictOrderSkipsAi() {
        handler.handle("aad-jan", "Jan", "sandwich: tuna");

        assertThat(ai.received()).isEmpty();
    }

    @Test
    void freeTextGoesToAiAndIsValidated() {
        ai.willReturn(Interpretation.order("sandwich", "tuna", ""));

        String reply = handler.handle("aad-jan", "Jan", "a tuna one please");

        assertThat(ai.received()).containsExactly("a tuna one please");
        assertThat(reply).isEqualTo("Got it, Jan: Sandwich, Tuna");
        assertThat(saved()).extracting(Order::item).containsExactly("Tuna");
    }

    @Test
    void aiItemNotOnMenuIsNotSaved() {
        ai.willReturn(Interpretation.order("Sandwich", "Salami", ""));

        String reply = handler.handle("aad-jan", "Jan", "salami please");

        assertThat(reply).startsWith("We don't have Salami.");
        assertThat(saved()).isEmpty();
    }

    @Test
    void strictItemNotOnMenuListsMenu() {
        String reply = handler.handle("aad-jan", "Jan", "sandwich: salami");

        assertThat(reply).isEqualTo("We don't have salami. On the Sandwich menu: Ham & cheese, Tuna, Chicken curry.");
        assertThat(saved()).isEmpty();
    }

    @Test
    void unknownFoodTypeListsFoodTypes() {
        String reply = handler.handle("aad-jan", "Jan", "pizza: margherita");

        assertThat(reply).isEqualTo("We don't have pizza. We do: Sandwich.");
        assertThat(saved()).isEmpty();
    }

    @Test
    void aiUnclearRepliesWithItsText() {
        ai.willReturn(Interpretation.unclear("Did you mean Ham & cheese or Tuna?"));

        String reply = handler.handle("aad-jan", "Jan", "the usual");

        assertThat(reply).isEqualTo("Did you mean Ham & cheese or Tuna?");
        assertThat(saved()).isEmpty();
    }

    @Test
    void closedWindowRefusesStrictOrder() {
        window.setOpen(false);

        assertThat(handler.handle("aad-jan", "Jan", "sandwich: tuna")).isEqualTo("Ordering is closed for today.");
        assertThat(saved()).isEmpty();
    }

    @Test
    void closedWindowRefusesAiOrder() {
        window.setOpen(false);
        ai.willReturn(Interpretation.order("Sandwich", "Tuna", ""));

        assertThat(handler.handle("aad-jan", "Jan", "tuna pls")).isEqualTo("Ordering is closed for today.");
        assertThat(saved()).isEmpty();
    }

    @Test
    void closedWindowRefusesCancel() {
        orders.save("aad-jan", "Jan", "Sandwich", "Tuna", "", TODAY, FakesConfig.NOW);
        window.setOpen(false);

        assertThat(handler.handle("aad-jan", "Jan", "cancel")).isEqualTo("Ordering is closed for today.");
        assertThat(saved()).hasSize(1);
    }

    @Test
    void menuAndMyOrderWorkWhenClosed() {
        orders.save("aad-jan", "Jan", "Sandwich", "Tuna", "no butter", TODAY, FakesConfig.NOW);
        window.setOpen(false);

        assertThat(handler.handle("aad-jan", "Jan", "menu"))
                .isEqualTo("Today's menu:\n\nSandwich: Ham & cheese, Tuna, Chicken curry");
        assertThat(handler.handle("aad-jan", "Jan", "my order")).isEqualTo("Jan: Sandwich, Tuna (no butter)");
    }

    @Test
    void myOrderWithoutOrder() {
        assertThat(handler.handle("aad-jan", "Jan", "my order")).isEqualTo("You have no order yet today.");
    }

    @Test
    void cancelRemovesOrderOrSaysThereIsNone() {
        orders.save("aad-jan", "Jan", "Sandwich", "Tuna", "", TODAY, FakesConfig.NOW);

        assertThat(handler.handle("aad-jan", "Jan", "cancel")).isEqualTo("Your order for today is cancelled.");
        assertThat(saved()).isEmpty();
        assertThat(handler.handle("aad-jan", "Jan", "cancel")).isEqualTo("You have no order to cancel.");
    }

    @Test
    void aiCancelIsHandledLikeCommand() {
        orders.save("aad-jan", "Jan", "Sandwich", "Tuna", "", TODAY, FakesConfig.NOW);
        ai.willReturn(Interpretation.of(Intent.CANCEL));

        assertThat(handler.handle("aad-jan", "Jan", "never mind my lunch")).isEqualTo("Your order for today is cancelled.");
        assertThat(saved()).isEmpty();
    }

    @Test
    void secondOrderReplacesFirst() {
        handler.handle("aad-jan", "Jan", "sandwich: tuna");
        handler.handle("aad-jan", "Jan", "sandwich: chicken curry");

        assertThat(saved()).extracting(Order::item).containsExactly("Chicken curry");
    }

    @Test
    void helpEndsWithAiNotice() {
        String reply = handler.handle("aad-jan", "Jan", "help");

        assertThat(reply).contains("menu", "my order", "cancel", "sandwich: ham & cheese, no butter");
        assertThat(reply).endsWith("Free-text messages are read by an AI service, so please don't include other people's details.");
    }

    @Test
    void notesTooLongAreRefusedNotSaved() {
        String reply = handler.handle("aad-jan", "Jan", "sandwich: tuna, " + "x".repeat(501));

        assertThat(reply).isEqualTo("Your notes are too long (max 500 characters).");
        assertThat(saved()).isEmpty();
    }

    @Test
    void aiNeverReceivesDisplayName() {
        ai.willReturn(Interpretation.order("Sandwich", "Tuna", ""));

        handler.handle("aad-jan", "Jan", "can I get a tuna sandwich");

        assertThat(ai.received()).singleElement().satisfies(text -> {
            assertThat(text).doesNotContain("Jan");
            assertThat(text).doesNotContain("aad-jan");
        });
    }
}
