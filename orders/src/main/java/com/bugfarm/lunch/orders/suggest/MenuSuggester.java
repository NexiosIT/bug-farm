package com.bugfarm.lunch.orders.suggest;

import com.bugfarm.lunch.contracts.MenuItem;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

/**
 * Picks the menu items most likely meant by a message the strict parser didn't recognise.
 * Pure word matching: exact words, word starts of three letters or more and small typos.
 * Words that match nothing ("can I get a", "pls", "aub") add nothing.
 */
@Component
public class MenuSuggester {

    static final int MAX_SUGGESTIONS = 3;

    private static final int EXACT = 3;
    private static final int CLOSE = 2;
    private static final int ITEM_WEIGHT = 2;
    private static final int FOOD_TYPE_WEIGHT = 1;

    /** At most three items, best first; ties keep the order of {@code menu}. */
    public List<MenuItem> suggest(String typed, List<MenuItem> menu) {
        List<String> typedWords = words(typed);
        int[] scores = menu.stream().mapToInt(item -> score(typedWords, item)).toArray();
        return IntStream.range(0, menu.size())
                .boxed()
                .sorted(Comparator.comparingInt((Integer i) -> scores[i]).reversed()) // stable: ties keep menu order
                .limit(MAX_SUGGESTIONS)
                .map(menu::get)
                .toList();
    }

    private static int score(List<String> typedWords, MenuItem item) {
        List<String> nameWords = words(item.name());
        List<String> typeWords = words(item.foodType());
        int total = 0;
        for (String word : typedWords) {
            total += Math.max(ITEM_WEIGHT * best(word, nameWords), FOOD_TYPE_WEIGHT * best(word, typeWords));
        }
        return total;
    }

    private static int best(String typed, List<String> targets) {
        return targets.stream().mapToInt(target -> wordScore(typed, target)).max().orElse(0);
    }

    static int wordScore(String typed, String target) {
        if (typed.equals(target)) {
            return EXACT;
        }
        if (typed.length() >= 3 && target.startsWith(typed)) {
            return CLOSE;
        }
        int allowed = typed.length() >= 7 ? 2 : typed.length() >= 4 ? 1 : 0;
        return allowed > 0 && distance(typed, target) <= allowed ? CLOSE : 0;
    }

    /** Lower-cased words; {@code &} counts as "and", and "and" itself is dropped. */
    static List<String> words(String text) {
        if (text == null) {
            return List.of();
        }
        String cleaned = text.toLowerCase(Locale.ROOT)
                .replace("&", " and ")
                .replaceAll("[^\\p{L}\\p{N}]+", " ");
        return Arrays.stream(cleaned.trim().split("\\s+"))
                .filter(w -> !w.isEmpty() && !w.equals("and"))
                .toList();
    }

    /** Optimal string alignment distance: edits plus swapping two neighbouring letters. */
    static int distance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }
}
