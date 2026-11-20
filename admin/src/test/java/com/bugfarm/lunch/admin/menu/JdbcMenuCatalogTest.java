package com.bugfarm.lunch.admin.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bugfarm.lunch.contracts.MenuItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;

@JdbcTest
@Import({MenuRepository.class, JdbcMenuCatalog.class})
class JdbcMenuCatalogTest {

    @Autowired
    private MenuRepository menu;

    @Autowired
    private JdbcMenuCatalog catalog;

    private long sandwichId;

    @BeforeEach
    void addHamAndCheese() {
        sandwichId = menu.all().stream()
                .filter(t -> t.name().equals("Sandwich"))
                .findFirst().orElseThrow().id();
        menu.addItem(sandwichId, "Ham & cheese");
    }

    @Test
    void findIsCaseInsensitiveAndTrimmed() {
        assertThat(catalog.find("  sandwich ", "HAM & CHEESE"))
                .get()
                .extracting(MenuItem::foodType, MenuItem::name)
                .containsExactly("Sandwich", "Ham & cheese");
    }

    @Test
    void unknownTypeGivesEmptyItems() {
        assertThat(catalog.itemsFor("Pizza")).isEmpty();
    }

    @Test
    void duplicateItemNameRejectedIgnoringCase() {
        assertThatThrownBy(() -> menu.addItem(sandwichId, " ham & CHEESE "))
                .isInstanceOf(DuplicateNameException.class);
    }
}
