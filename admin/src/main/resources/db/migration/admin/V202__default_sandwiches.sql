INSERT INTO menu_item (food_type_id, name)
SELECT t.id, v.name
FROM food_type t
JOIN (
    SELECT 'Cheese' AS name
    UNION ALL SELECT 'Chicken curry'
    UNION ALL SELECT 'Ham & cheese'
    UNION ALL SELECT 'Martino'
    UNION ALL SELECT 'Tuna'
) v ON 1 = 1
WHERE t.name = 'Sandwich'
  AND NOT EXISTS (
    SELECT 1 FROM menu_item m WHERE m.food_type_id = t.id AND m.name = v.name
  );
