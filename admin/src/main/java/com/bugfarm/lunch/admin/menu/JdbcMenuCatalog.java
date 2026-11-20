package com.bugfarm.lunch.admin.menu;

import com.bugfarm.lunch.contracts.MenuCatalog;
import com.bugfarm.lunch.contracts.MenuItem;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class JdbcMenuCatalog implements MenuCatalog {

    private static final String ITEMS = "SELECT i.id, t.name AS food_type, i.name FROM menu_item i"
            + " JOIN food_type t ON t.id = i.food_type_id";

    private final JdbcClient jdbc;

    public JdbcMenuCatalog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<String> foodTypes() {
        return jdbc.sql("SELECT t.name FROM food_type t"
                        + " WHERE EXISTS (SELECT 1 FROM menu_item i WHERE i.food_type_id = t.id)"
                        + " ORDER BY LOWER(t.name)")
                .query(String.class).list();
    }

    @Override
    public List<MenuItem> itemsFor(String foodType) {
        return jdbc.sql(ITEMS + " WHERE LOWER(t.name) = LOWER(:type) ORDER BY LOWER(i.name)")
                .param("type", trim(foodType))
                .query((rs, n) -> new MenuItem(rs.getLong("id"), rs.getString("food_type"), rs.getString("name")))
                .list();
    }

    @Override
    public Optional<MenuItem> find(String foodType, String itemName) {
        return jdbc.sql(ITEMS + " WHERE LOWER(t.name) = LOWER(:type) AND LOWER(i.name) = LOWER(:item)")
                .param("type", trim(foodType)).param("item", trim(itemName))
                .query((rs, n) -> new MenuItem(rs.getLong("id"), rs.getString("food_type"), rs.getString("name")))
                .optional();
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
