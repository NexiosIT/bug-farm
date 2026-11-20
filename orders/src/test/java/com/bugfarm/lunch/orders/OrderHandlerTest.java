package com.bugfarm.lunch.orders;

import static org.assertj.core.api.Assertions.assertThat;

import com.bugfarm.lunch.contracts.Order;
import com.bugfarm.lunch.orders.fakes.FakeMenuCatalog;
import com.bugfarm.lunch.orders.fakes.FakeOrderingWindow;
import com.bugfarm.lunch.orders.fakes.FakesConfig;
import com.bugfarm.lunch.orders.parse.CommandParser;
import com.bugfarm.lunch.orders.suggest.MenuSuggester;
import com.bugfarm.lunch.orders.suggest.OpenChoices;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;

@JdbcTest
@Import({FakesConfig.class, JdbcOrderService.class, CommandParser.class, MenuSuggester.class, OpenChoices.class,
        OrderHandler.class})
class OrderHandlerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);
    private static final String NO_OPEN_QUESTION =
            "I have no open question for you. Mention me with e.g. sandwich: ham & cheese.";

    @Autowired OrderHandler handler;
    @Autowired JdbcOrderService orders;
    @Autowired FakeMenuCatalog menu;
    @Autowired FakeOrderingWindow window;
    @Autowired OpenChoices choices;

    @BeforeEach
    void reset() {
        window.setOpen(true);
        menu.reset();
        choices.clear("aad-jan");
        choices.clear("aad-piet");
    }

    private List<Order> saved() {
        return orders.ordersFor(TODAY);
    }

    private String jan(String text) {
        return handler.handle("aad-jan", "Jan", text);
    }

    // --- Strict format and commands (unchanged behaviour) ---

    @Test
    void strictOrderSavesWithMenuSpelling() {
        String reply = jan("sandwich: HAM & CHEESE, no butter");

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
        assertThat(jan("sandwich: tuna")).isEqualTo("Got it, Jan: Sandwich, Tuna");
    }

    @Test
    void strictItemNotOnMenuListsMenu() {
        String reply = jan("sandwich: salami");

        assertThat(reply).isEqualTo("We don't have salami. On the Sandwich menu: Ham & cheese, Tuna, Chicken curry.");
        assertThat(saved()).isEmpty();
    }

    @Test
    void unknownFoodTypeListsFoodTypes() {
        String reply = jan("pizza: margherita");

        assertThat(reply).isEqualTo("We don't have pizza. We do: Sandwich.");
        assertThat(saved()).isEmpty();
    }

    @Test
    void closedWindowRefusesStrictOrder() {
        window.setOpen(false);

        assertThat(jan("sandwich: tuna")).isEqualTo("Ordering is closed for today.");
        assertThat(saved()).isEmpty();
    }

    @Test
    void closedWindowRefusesCancel() {
        orders.save("aad-jan", "Jan", "Sandwich", "Tuna", "", TODAY, FakesConfig.NOW);
        window.setOpen(false);

        assertThat(jan("cancel")).isEqualTo("Ordering is closed for today.");
        assertThat(saved()).hasSize(1);
    }

    @Test
    void menuAndMyOrderWorkWhenClosed() {
        orders.save("aad-jan", "Jan", "Sandwich", "Tuna", "no butter", TODAY, FakesConfig.NOW);
        window.setOpen(false);

        assertThat(jan("menu")).isEqualTo("Today's menu:\n\nSandwich: Ham & cheese, Tuna, Chicken curry");
        assertThat(jan("my order")).isEqualTo("Jan: Sandwich, Tuna (no butter)");
    }

    @Test
    void myOrderWithoutOrder() {
        assertThat(jan("my order")).isEqualTo("You have no order yet today.");
    }

    @Test
    void cancelRemovesOrderOrSaysThereIsNone() {
        orders.save("aad-jan", "Jan", "Sandwich", "Tuna", "", TODAY, FakesConfig.NOW);

        assertThat(jan("cancel")).isEqualTo("Your order for today is cancelled.");
        assertThat(saved()).isEmpty();
        assertThat(jan("cancel")).isEqualTo("You have no order to cancel.");
    }

    @Test
    void secondOrderReplacesFirst() {
        jan("sandwich: tuna");
        jan("sandwich: chicken curry");

        assertThat(saved()).extracting(Order::item).containsExactly("Chicken curry");
    }

    @Test
    void helpExplainsNumberedSuggestions() {
        String reply = jan("help");

        assertThat(reply).contains("menu", "my order", "cancel", "sandwich: ham & cheese, no butter", "1, 2 or 3");
        assertThat(reply).doesNotContain("AI");
    }

    @Test
    void notesTooLongAreRefusedNotSaved() {
        String reply = jan("sandwich: tuna, " + "x".repeat(501));

        assertThat(reply).isEqualTo("Your notes are too long (max 500 characters).");
        assertThat(saved()).isEmpty();
    }

    // --- Suggestions and number answers ---

    @Test
    void unrecognisedTextOffersThreeNumberedOptions() {
        assertThat(jan("tuna pls")).isEqualTo(String.join("\n\n",
                "Did you mean:",
                "1. Sandwich, Tuna",
                "2. Sandwich, Ham & cheese",
                "3. Sandwich, Chicken curry",
                "Reply with @SandwichBot 1, 2 or 3."));
        assertThat(saved()).isEmpty();
    }

    @Test
    void numberOrdersTheOfferedItemWithNotes() {
        jan("tuna, no butter");

        assertThat(jan("1")).isEqualTo("Got it, Jan: Sandwich, Tuna (no butter)");
        assertThat(saved()).singleElement().satisfies(o -> {
            assertThat(o.item()).isEqualTo("Tuna");
            assertThat(o.notes()).isEqualTo("no butter");
        });
    }

    @Test
    void hashNumberAndSpacesAreAccepted() {
        jan("tuna");

        assertThat(jan(" #1 ")).isEqualTo("Got it, Jan: Sandwich, Tuna");
    }

    @Test
    void numberWithTrailingPunctuationIsAnAnswer() {
        jan("tuna");
        assertThat(jan("1.")).isEqualTo("Got it, Jan: Sandwich, Tuna");

        jan("tuna");
        assertThat(jan("2!")).isEqualTo("Got it, Jan: Sandwich, Ham & cheese");
    }

    @Test
    void numberWithNotesReplacesTheNotes() {
        jan("tuna, no butter");

        assertThat(jan("1, extra mayo")).isEqualTo("Got it, Jan: Sandwich, Tuna (extra mayo)");
    }

    @Test
    void fullWidthDigitIsAnAnswer() {
        jan("tuna");

        assertThat(jan("１")).isEqualTo("Got it, Jan: Sandwich, Tuna");
    }

    @Test
    void unclearNumberAnswerAsksAgainAndKeepsOptions() {
        jan("tuna, no butter");

        assertThat(jan("1 please")).isEqualTo("Please answer 1, 2 or 3.");
        assertThat(jan("1")).isEqualTo("Got it, Jan: Sandwich, Tuna (no butter)");
    }

    @Test
    void numberWithoutOpenQuestion() {
        assertThat(jan("2")).isEqualTo(NO_OPEN_QUESTION);
        assertThat(saved()).isEmpty();
    }

    @Test
    void numberFromAnotherPersonDoesNotUseTheirOptions() {
        jan("tuna");

        assertThat(handler.handle("aad-piet", "Piet", "1")).isEqualTo(NO_OPEN_QUESTION);
        assertThat(saved()).isEmpty();
        assertThat(jan("1")).isEqualTo("Got it, Jan: Sandwich, Tuna");
    }

    @Test
    void numberOutOfRangeKeepsOptions() {
        jan("tuna");

        assertThat(jan("4")).isEqualTo("Please answer 1, 2 or 3.");
        assertThat(jan("0")).isEqualTo("Please answer 1, 2 or 3.");
        assertThat(saved()).isEmpty();
        assertThat(jan("1")).isEqualTo("Got it, Jan: Sandwich, Tuna");
    }

    @Test
    void answeredOptionsAreCleared() {
        jan("tuna");
        jan("1");

        assertThat(jan("2")).isEqualTo(NO_OPEN_QUESTION);
        assertThat(saved()).hasSize(1);
    }

    @Test
    void recognisedCommandClearsOptions() {
        jan("tuna");
        jan("menu");

        assertThat(jan("1")).isEqualTo(NO_OPEN_QUESTION);
    }

    @Test
    void numberAfterCutOffIsRefusedAndClearsOptions() {
        jan("tuna");
        window.setOpen(false);

        assertThat(jan("1")).isEqualTo("Ordering is closed for today.");
        window.setOpen(true);
        assertThat(jan("1")).isEqualTo(NO_OPEN_QUESTION);
        assertThat(saved()).isEmpty();
    }

    @Test
    void pickedItemRemovedFromMenuIsNotSaved() {
        jan("tuna");
        menu.removeItem("Tuna");

        assertThat(jan("1")).isEqualTo("That item is no longer on the menu.");
        assertThat(saved()).isEmpty();
    }

    @Test
    void unrecognisedTextWhenClosedOffersNothing() {
        window.setOpen(false);

        assertThat(jan("tuna pls")).isEqualTo("Ordering is closed for today.");
        window.setOpen(true);
        assertThat(jan("1")).isEqualTo(NO_OPEN_QUESTION);
    }

    @Test
    void emptyMenuSaysSo() {
        menu.clear();

        assertThat(jan("tuna pls")).isEqualTo("There is nothing on the menu yet.");
    }

    @Test
    void fewerOptionsChangeTheFooter() {
        menu.removeItem("Chicken curry");

        assertThat(jan("tuna")).endsWith("Reply with @SandwichBot 1 or 2.");
        assertThat(jan("3")).isEqualTo("Please answer 1 or 2.");
    }
}
