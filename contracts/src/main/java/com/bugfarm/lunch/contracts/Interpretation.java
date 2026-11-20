package com.bugfarm.lunch.contracts;

/** What a chat message means. String fields are never null; unused ones are {@code ""}. */
public record Interpretation(Intent intent, String foodType, String item, String notes, String reply) {

    public Interpretation {
        foodType = foodType == null ? "" : foodType;
        item = item == null ? "" : item;
        notes = notes == null ? "" : notes;
        reply = reply == null ? "" : reply;
    }

    /** An interpretation with only the intent set; all other fields {@code ""}. */
    public static Interpretation of(Intent intent) {
        return new Interpretation(intent, "", "", "", "");
    }

    /** An ORDER for the given food type, item and notes; reply is {@code ""}. */
    public static Interpretation order(String foodType, String item, String notes) {
        return new Interpretation(Intent.ORDER, foodType, item, notes, "");
    }

    /** UNCLEAR with a reply to show the user; all other fields {@code ""}. */
    public static Interpretation unclear(String reply) {
        return new Interpretation(Intent.UNCLEAR, "", "", "", reply);
    }
}
