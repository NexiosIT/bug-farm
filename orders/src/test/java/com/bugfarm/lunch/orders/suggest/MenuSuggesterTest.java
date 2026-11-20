package com.bugfarm.lunch.orders.suggest;

import static org.assertj.core.api.Assertions.assertThat;

import com.bugfarm.lunch.contracts.MenuItem;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MenuSuggesterTest {

    private static final List<MenuItem> MENU = menu(
            "Salad", "Caesar", "Greek",
            "Sandwich", "Brie & honey", "Chicken curry", "Egg salad", "Ham & cheese", "Martino",
            "Mozzarella & tomato", "Smos", "Tuna",
            "Soup", "Soup of the day", "Tomato",
            "Wrap", "Chicken caesar", "Falafel");

    private final MenuSuggester suggester = new MenuSuggester();

    /** Entries that are one of the four food types start a new type; the rest are its items (ids 1..n). */
    private static List<MenuItem> menu(String... entries) {
        List<String> types = List.of("Salad", "Sandwich", "Soup", "Wrap");
        List<MenuItem> items = new ArrayList<>();
        String type = null;
        for (String entry : entries) {
            if (types.contains(entry)) {
                type = entry;
            } else {
                items.add(new MenuItem(items.size() + 1, type, entry));
            }
        }
        return List.copyOf(items);
    }

    private String first(String typed) {
        MenuItem best = suggester.suggest(typed, MENU).get(0);
        return best.foodType() + ", " + best.name();
    }

    @Test
    void testMenuHasFourteenItems() {
        assertThat(MENU).hasSize(14);
        assertThat(MENU.get(0)).isEqualTo(new MenuItem(1, "Salad", "Caesar"));
        assertThat(MENU.get(13)).isEqualTo(new MenuItem(14, "Wrap", "Falafel"));
    }

    @Test
    void exactItemWordWins() {
        assertThat(first("Brie")).isEqualTo("Sandwich, Brie & honey");
    }

    @Test
    void fillerWordsAreIgnored() {
        assertThat(first("smos aub")).isEqualTo("Sandwich, Smos");
    }

    @Test
    void andMatchesAmpersand() {
        assertThat(first("ham and cheese")).isEqualTo("Sandwich, Ham & cheese");
        assertThat(first("HAM & CHEESE")).isEqualTo("Sandwich, Ham & cheese");
    }

    @Test
    void prefixOfThreeLetters() {
        assertThat(first("moz")).isEqualTo("Sandwich, Mozzarella & tomato");
    }

    @Test
    void foodTypeHelps() {
        assertThat(first("wrap falafel")).isEqualTo("Wrap, Falafel");
        assertThat(first("chicken wrap")).isEqualTo("Wrap, Chicken caesar");
    }

    @Test
    void swappedLettersAreATypo() {
        assertThat(first("brei")).isEqualTo("Sandwich, Brie & honey");
    }

    @Test
    void returnsAtMostThree() {
        assertThat(suggester.suggest("chicken", MENU)).hasSize(3);
    }

    @Test
    void fewerItemsThanThree() {
        assertThat(suggester.suggest("tuna", MENU.subList(0, 2))).hasSize(2);
    }

    @Test
    void nonsenseKeepsMenuOrder() {
        assertThat(suggester.suggest("hello", MENU)).extracting(MenuItem::id).containsExactly(1L, 2L, 3L);
    }

    @Test
    void emptyMenuGivesNothing() {
        assertThat(suggester.suggest("tuna", List.of())).isEmpty();
    }

    @Test
    void nullOrBlankTextIsNonsense() {
        assertThat(suggester.suggest(null, MENU)).extracting(MenuItem::id).containsExactly(1L, 2L, 3L);
        assertThat(suggester.suggest("  ", MENU)).hasSize(3);
    }
}
