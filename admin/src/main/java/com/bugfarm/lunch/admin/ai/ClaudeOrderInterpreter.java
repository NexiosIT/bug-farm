package com.bugfarm.lunch.admin.ai;

import com.bugfarm.lunch.contracts.Intent;
import com.bugfarm.lunch.contracts.Interpretation;
import com.bugfarm.lunch.contracts.MenuCatalog;
import com.bugfarm.lunch.contracts.MenuItem;
import com.bugfarm.lunch.contracts.OrderInterpreter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reads free text with Claude. Sends only the message text and today's menu. Never throws:
 * any failure gives UNCLEAR with the fixed fallback text. The orders module still checks
 * every ORDER against the menu before saving.
 */
@Component
public class ClaudeOrderInterpreter implements OrderInterpreter {

    static final String FALLBACK = "Sorry, I couldn't read that. Try e.g. sandwich: ham & cheese, no butter.";

    private static final Logger log = LoggerFactory.getLogger(ClaudeOrderInterpreter.class);

    private final MenuCatalog menu;
    private final ClaudeCaller caller;

    public ClaudeOrderInterpreter(MenuCatalog menu, ClaudeCaller caller) {
        this.menu = menu;
        this.caller = caller;
    }

    @Override
    public Interpretation interpret(String messageText) {
        try {
            String system = InterpreterPrompt.system(currentMenu());
            Optional<AiResult> result = caller.call(system, messageText == null ? "" : messageText);
            return result.map(ClaudeOrderInterpreter::toInterpretation)
                    .orElseGet(() -> Interpretation.unclear(FALLBACK));
        } catch (RuntimeException e) {
            // Never log the message text: it may contain personal details.
            log.warn("AI interpretation failed: {}", e.getClass().getSimpleName());
            return Interpretation.unclear(FALLBACK);
        }
    }

    private Map<String, List<String>> currentMenu() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String foodType : menu.foodTypes()) {
            result.put(foodType, menu.itemsFor(foodType).stream().map(MenuItem::name).toList());
        }
        return result;
    }

    private static Interpretation toInterpretation(AiResult r) {
        if (r.intent() == null) {
            return Interpretation.unclear(FALLBACK);
        }
        if (r.intent() == Intent.UNCLEAR) {
            return Interpretation.unclear(r.reply() == null || r.reply().isBlank() ? FALLBACK : r.reply().trim());
        }
        return new Interpretation(r.intent(), r.foodType(), r.item(), r.notes(), "");
    }
}
