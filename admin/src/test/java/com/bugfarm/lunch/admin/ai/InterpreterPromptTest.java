package com.bugfarm.lunch.admin.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InterpreterPromptTest {

    private static Map<String, List<String>> menu() {
        Map<String, List<String>> menu = new LinkedHashMap<>();
        menu.put("Sandwich", List.of("Chicken curry", "Ham & cheese", "Tuna"));
        menu.put("Soup", List.of("Tomato"));
        return menu;
    }

    private static String insideMenuTags(String prompt) {
        return prompt.substring(prompt.indexOf("<menu>") + "<menu>".length(), prompt.indexOf("</menu>"));
    }

    private static String outsideMenuTags(String prompt) {
        return prompt.substring(0, prompt.indexOf("<menu>")) + prompt.substring(prompt.indexOf("</menu>"));
    }

    @Test
    void listsEveryMenuItemExactly() {
        String menuText = insideMenuTags(InterpreterPrompt.system(menu()));

        assertThat(menuText).isEqualTo("""

                Sandwich:
                - Chicken curry
                - Ham & cheese
                - Tuna
                Soup:
                - Tomato
                """);
    }

    @Test
    void containsNoPersonalFields() {
        String prompt = InterpreterPrompt.system(menu());
        String other = InterpreterPrompt.system(Map.of("Pasta", List.of("Pesto")));

        // Outside the menu block the prompt is fixed instruction text, the same for every call.
        assertThat(outsideMenuTags(prompt)).isEqualTo(outsideMenuTags(other));
        // Inside it there is nothing but food types and items.
        assertThat(insideMenuTags(prompt).lines().filter(l -> !l.isBlank()))
                .allMatch(l -> l.equals("Sandwich:") || l.equals("Soup:") || l.startsWith("- "));
        assertThat(prompt.toLowerCase())
                .doesNotContain("aad", "teamsuserid", "displayname", "user id");
    }

    @Test
    void explainsIntentsAndRules() {
        String prompt = InterpreterPrompt.system(menu());

        assertThat(prompt).contains("Teams", "ORDER", "CANCEL", "MY_ORDER", "MENU", "HELP", "UNCLEAR",
                "exact item name", "notes", "never invent");
    }
}
