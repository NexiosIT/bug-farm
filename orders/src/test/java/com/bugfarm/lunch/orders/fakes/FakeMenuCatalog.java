package com.bugfarm.lunch.orders.fakes;

import com.bugfarm.lunch.contracts.MenuCatalog;
import com.bugfarm.lunch.contracts.MenuItem;
import java.util.List;
import java.util.Optional;

/** Sandwich with Ham & cheese, Tuna and Chicken curry. Matching follows the MenuCatalog contract. */
public class FakeMenuCatalog implements MenuCatalog {

    private final List<MenuItem> items = List.of(
            new MenuItem(1, "Sandwich", "Ham & cheese"),
            new MenuItem(2, "Sandwich", "Tuna"),
            new MenuItem(3, "Sandwich", "Chicken curry"));

    @Override
    public List<String> foodTypes() {
        return items.stream().map(MenuItem::foodType).distinct().sorted().toList();
    }

    @Override
    public List<MenuItem> itemsFor(String foodType) {
        String type = foodType == null ? "" : foodType.trim();
        return items.stream().filter(i -> i.foodType().equalsIgnoreCase(type)).toList();
    }

    @Override
    public Optional<MenuItem> find(String foodType, String itemName) {
        String name = itemName == null ? "" : itemName.trim();
        return itemsFor(foodType).stream().filter(i -> i.name().equalsIgnoreCase(name)).findFirst();
    }
}
