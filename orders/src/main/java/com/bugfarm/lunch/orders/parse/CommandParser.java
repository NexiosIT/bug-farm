package com.bugfarm.lunch.orders.parse;

import com.bugfarm.lunch.contracts.Intent;
import com.bugfarm.lunch.contracts.Interpretation;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Reads the strict message format: the fixed commands and {@code <food type>: <item>[, <notes>]}.
 * Anything else gives {@link Optional#empty()}; the handler then offers menu suggestions.
 */
@Component
public class CommandParser {

    private static final Map<String, Intent> COMMANDS = Map.of(
            "menu", Intent.MENU,
            "my order", Intent.MY_ORDER,
            "cancel", Intent.CANCEL,
            "help", Intent.HELP);

    private static final Pattern ORDER = Pattern.compile("^([^:,]+):([^,]+)(?:,(.*))?$", Pattern.DOTALL);

    public Optional<Interpretation> parse(String cleanedText) {
        if (cleanedText == null) {
            return Optional.empty();
        }
        String text = cleanedText.trim();
        Intent command = COMMANDS.get(text.toLowerCase(Locale.ROOT));
        if (command != null) {
            return Optional.of(Interpretation.of(command));
        }
        Matcher m = ORDER.matcher(text);
        if (!m.matches()) {
            return Optional.empty();
        }
        String foodType = m.group(1).trim();
        String item = m.group(2).trim();
        String notes = m.group(3) == null ? "" : m.group(3).trim();
        if (foodType.isEmpty() || item.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Interpretation.order(foodType, item, notes));
    }
}
