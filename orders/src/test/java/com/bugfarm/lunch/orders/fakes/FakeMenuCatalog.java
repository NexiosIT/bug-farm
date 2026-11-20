package com.bugfarm.lunch.orders.fakes;

import com.bugfarm.lunch.contracts.MenuCatalog;
import com.bugfarm.lunch.contracts.MenuItem;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sandwich with Ham & cheese, Tuna and Chicken curry; tests can remove items.
 * Matching follows the MenuCatalog contract.
 */
public class FakeMenuCatalog implements MenuCatalog {

    private static final List<MenuItem> START = List.of(
            new MenuItem(1, "Sandwich", "Ham & cheese"),
            new MenuItem(2, "Sandwich", "Tuna"),
            new MenuItem(3, "Sandwich", "Chicken curry"));

    private final List<MenuItem> items = new CopyOnWriteArrayList<>(START);

    /** Back to the three sandwiches. */
    public void reset() {
        items.clear();
        items.addAll(START);
    }

    public void removeItem(String name) {
        items.removeIf(i -> i.name().equals(name));
    }

    public void clear() {
        items.clear();
    }

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
