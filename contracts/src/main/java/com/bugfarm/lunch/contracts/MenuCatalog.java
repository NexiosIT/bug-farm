package com.bugfarm.lunch.contracts;

import java.util.List;
import java.util.Optional;

public interface MenuCatalog {
    /** Food types in menu spelling, sorted. */
    List<String> foodTypes();

    /** Items of the food type (case-insensitive); empty if the type is unknown. */
    List<MenuItem> itemsFor(String foodType);

    /** Finds an item; food type and item name are matched case-insensitive and trimmed. */
    Optional<MenuItem> find(String foodType, String itemName);
}
