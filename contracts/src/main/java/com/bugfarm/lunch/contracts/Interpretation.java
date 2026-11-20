package com.bugfarm.lunch.contracts;

/** What a chat message means. String fields are never null; unused ones are {@code ""}. */
public record Interpretation(Intent intent, String foodType, String item, String notes) {

    public Interpretation {
        foodType = foodType == null ? "" : foodType;
        item = item == null ? "" : item;
        notes = notes == null ? "" : notes;
    }

    /** An interpretation with only the intent set; all other fields {@code ""}. */
    public static Interpretation of(Intent intent) {
        return new Interpretation(intent, "", "", "");
    }

    /** An ORDER for the given food type, item and notes. */
    public static Interpretation order(String foodType, String item, String notes) {
        return new Interpretation(Intent.ORDER, foodType, item, notes);
    }
}
