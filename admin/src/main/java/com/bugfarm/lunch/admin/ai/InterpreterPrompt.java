package com.bugfarm.lunch.admin.ai;

import java.util.List;
import java.util.Map;

/**
 * The system prompt for reading one lunch message. Built only from fixed instructions
 * and today's menu: never a name, Teams id or anything else about the sender.
 */
public final class InterpreterPrompt {

    private static final String BEFORE_MENU = """
            You read one lunch-order message from a colleague in an office Microsoft Teams channel \
            and work out what they want. Today's menu, by food type:
            <menu>""";

    private static final String AFTER_MENU = """
            </menu>

            Choose exactly one intent:
            - ORDER: they want to order an item from the menu for today.
            - CANCEL: they want to cancel today's order.
            - MY_ORDER: they ask what they have ordered today.
            - MENU: they ask what is on the menu.
            - HELP: they ask how to use the bot.
            - UNCLEAR: anything else.

            Rules:
            - For ORDER, set foodType and item to the exact item name and food type as spelled on the menu above. \
            Map near-spellings, abbreviations and different word order to that exact item name \
            (for example "ham cheese" means "Ham & cheese").
            - Put extras and wishes such as "no butter" or "extra pickles" in notes. Otherwise leave notes empty.
            - Use UNCLEAR when the item is not on the menu, when it could be more than one item, \
            or when the message is not about lunch. Then write a short, friendly reply of at most 2 sentences \
            that names the closest menu options, for example: "We don't have salami. Did you mean Ham & cheese or Tuna?"
            - You never invent items: only items listed on the menu above can be ordered.
            - Leave fields that do not apply empty.""";

    private InterpreterPrompt() {
    }

    /** @param menu food type → item names, both in menu spelling and in display order */
    public static String system(Map<String, List<String>> menu) {
        StringBuilder prompt = new StringBuilder(BEFORE_MENU).append('\n');
        menu.forEach((foodType, items) -> {
            prompt.append(foodType).append(":\n");
            items.forEach(item -> prompt.append("- ").append(item).append('\n'));
        });
        return prompt.append(AFTER_MENU).toString();
    }
}
