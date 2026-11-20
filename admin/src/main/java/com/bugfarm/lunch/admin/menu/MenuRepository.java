package com.bugfarm.lunch.admin.menu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Food types and their items. Names are trimmed; duplicates are compared
 * case-insensitive (food types overall, items within their food type).
 */
@Repository
public class MenuRepository {

    static final int MAX_FOOD_TYPE_NAME = 100;
    static final int MAX_ITEM_NAME = 200;

    private final JdbcClient jdbc;

    public MenuRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** All food types with their items, both sorted by name (case-insensitive). */
    public List<FoodTypeView> all() {
        Map<Long, String> types = new LinkedHashMap<>();
        jdbc.sql("SELECT id, name FROM food_type ORDER BY LOWER(name), id")
                .query(rs -> {
                    types.put(rs.getLong("id"), rs.getString("name"));
                });
        Map<Long, List<ItemView>> items = new LinkedHashMap<>();
        jdbc.sql("SELECT id, food_type_id, name FROM menu_item ORDER BY LOWER(name), id")
                .query(rs -> {
                    items.computeIfAbsent(rs.getLong("food_type_id"), k -> new ArrayList<>())
                            .add(new ItemView(rs.getLong("id"), rs.getString("name")));
                });
        List<FoodTypeView> result = new ArrayList<>();
        types.forEach((id, name) -> result.add(new FoodTypeView(id, name, items.getOrDefault(id, List.of()))));
        return result;
    }

    @Transactional
    public FoodTypeView createFoodType(String rawName) {
        String name = clean(rawName, MAX_FOOD_TYPE_NAME);
        if (foodTypeNameTaken(name, -1)) {
            throw new DuplicateNameException(name);
        }
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO food_type (name) VALUES (:name)")
                .param("name", name)
                .update(keys, "id");
        return new FoodTypeView(keys.getKey().longValue(), name, List.of());
    }

    @Transactional
    public FoodTypeView renameFoodType(long id, String rawName) {
        String name = clean(rawName, MAX_FOOD_TYPE_NAME);
        requireFoodType(id);
        if (foodTypeNameTaken(name, id)) {
            throw new DuplicateNameException(name);
        }
        jdbc.sql("UPDATE food_type SET name = :name WHERE id = :id")
                .param("name", name).param("id", id).update();
        return all().stream().filter(t -> t.id() == id).findFirst().orElseThrow();
    }

    @Transactional
    public void deleteFoodType(long id) {
        if (jdbc.sql("DELETE FROM food_type WHERE id = :id").param("id", id).update() == 0) {
            throw new NotFoundException("Food type", id);
        }
    }

    @Transactional
    public ItemView addItem(long foodTypeId, String rawName) {
        String name = clean(rawName, MAX_ITEM_NAME);
        requireFoodType(foodTypeId);
        if (itemNameTaken(foodTypeId, name, -1)) {
            throw new DuplicateNameException(name);
        }
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO menu_item (food_type_id, name) VALUES (:type, :name)")
                .param("type", foodTypeId).param("name", name)
                .update(keys, "id");
        return new ItemView(keys.getKey().longValue(), name);
    }

    @Transactional
    public ItemView renameItem(long id, String rawName) {
        String name = clean(rawName, MAX_ITEM_NAME);
        Long foodTypeId = jdbc.sql("SELECT food_type_id FROM menu_item WHERE id = :id")
                .param("id", id).query(Long.class).optional()
                .orElseThrow(() -> new NotFoundException("Item", id));
        if (itemNameTaken(foodTypeId, name, id)) {
            throw new DuplicateNameException(name);
        }
        jdbc.sql("UPDATE menu_item SET name = :name WHERE id = :id")
                .param("name", name).param("id", id).update();
        return new ItemView(id, name);
    }

    @Transactional
    public void deleteItem(long id) {
        if (jdbc.sql("DELETE FROM menu_item WHERE id = :id").param("id", id).update() == 0) {
            throw new NotFoundException("Item", id);
        }
    }

    private static String clean(String rawName, int maxLength) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            throw new InvalidNameException("Name must not be blank");
        }
        if (name.length() > maxLength) {
            throw new InvalidNameException("Name must be at most " + maxLength + " characters");
        }
        return name;
    }

    private void requireFoodType(long id) {
        boolean exists = jdbc.sql("SELECT COUNT(*) FROM food_type WHERE id = :id")
                .param("id", id).query(Long.class).single() > 0;
        if (!exists) {
            throw new NotFoundException("Food type", id);
        }
    }

    private boolean foodTypeNameTaken(String name, long exceptId) {
        return jdbc.sql("SELECT COUNT(*) FROM food_type WHERE LOWER(name) = LOWER(:name) AND id <> :id")
                .param("name", name).param("id", exceptId)
                .query(Long.class).single() > 0;
    }

    private boolean itemNameTaken(long foodTypeId, String name, long exceptId) {
        return jdbc.sql("SELECT COUNT(*) FROM menu_item WHERE food_type_id = :type"
                        + " AND LOWER(name) = LOWER(:name) AND id <> :id")
                .param("type", foodTypeId).param("name", name).param("id", exceptId)
                .query(Long.class).single() > 0;
    }
}
