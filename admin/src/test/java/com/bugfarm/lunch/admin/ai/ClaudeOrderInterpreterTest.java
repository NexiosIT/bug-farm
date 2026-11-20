package com.bugfarm.lunch.admin.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.bugfarm.lunch.contracts.Intent;
import com.bugfarm.lunch.contracts.Interpretation;
import com.bugfarm.lunch.contracts.MenuCatalog;
import com.bugfarm.lunch.contracts.MenuItem;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ClaudeOrderInterpreterTest {

    private static final String FALLBACK = "Sorry, I couldn't read that. Try e.g. sandwich: ham & cheese, no butter.";

    /** Sandwich: Ham & cheese, Tuna. */
    private static final MenuCatalog MENU = new MenuCatalog() {
        private final List<MenuItem> items = List.of(
                new MenuItem(1, "Sandwich", "Ham & cheese"),
                new MenuItem(2, "Sandwich", "Tuna"));

        @Override
        public List<String> foodTypes() {
            return List.of("Sandwich");
        }

        @Override
        public List<MenuItem> itemsFor(String foodType) {
            return foodType.trim().equalsIgnoreCase("Sandwich") ? items : List.of();
        }

        @Override
        public Optional<MenuItem> find(String foodType, String itemName) {
            return itemsFor(foodType).stream().filter(i -> i.name().equalsIgnoreCase(itemName.trim())).findFirst();
        }
    };

    /** Returns a preset answer (or throws) and records what it was called with. */
    private static final class FakeCaller implements ClaudeCaller {
        private final Optional<AiResult> answer;
        private final RuntimeException failure;
        String systemPrompt;
        String userText;

        FakeCaller(Optional<AiResult> answer) {
            this.answer = answer;
            this.failure = null;
        }

        FakeCaller(RuntimeException failure) {
            this.answer = Optional.empty();
            this.failure = failure;
        }

        @Override
        public Optional<AiResult> call(String systemPrompt, String userText) {
            this.systemPrompt = systemPrompt;
            this.userText = userText;
            if (failure != null) {
                throw failure;
            }
            return answer;
        }
    }

    @Test
    void mapsOrderResult() {
        FakeCaller caller = new FakeCaller(Optional.of(
                new AiResult(Intent.ORDER, "Sandwich", "Ham & cheese", "no butter", "")));

        Interpretation result = new ClaudeOrderInterpreter(MENU, caller).interpret("ham cheese no butter pls");

        assertThat(result).isEqualTo(Interpretation.order("Sandwich", "Ham & cheese", "no butter"));
    }

    @Test
    void emptyResultGivesFallbackText() {
        Interpretation result = new ClaudeOrderInterpreter(MENU, new FakeCaller(Optional.empty())).interpret("x");

        assertThat(result).isEqualTo(Interpretation.unclear(FALLBACK));
    }

    @Test
    void callerExceptionGivesFallbackText() {
        FakeCaller caller = new FakeCaller(new IllegalStateException("boom"));

        Interpretation result = new ClaudeOrderInterpreter(MENU, caller).interpret("x");

        assertThat(result).isEqualTo(Interpretation.unclear(FALLBACK));
    }

    @Test
    void passesOnlyMessageTextAsUserTurn() {
        FakeCaller caller = new FakeCaller(Optional.empty());

        new ClaudeOrderInterpreter(MENU, caller).interpret("can I get a tuna one");

        assertThat(caller.userText).isEqualTo("can I get a tuna one");
        assertThat(caller.systemPrompt).isEqualTo(InterpreterPrompt.system(
                java.util.Map.of("Sandwich", List.of("Ham & cheese", "Tuna"))));
    }

    @Test
    void unclearReplyPassedThrough() {
        FakeCaller caller = new FakeCaller(Optional.of(new AiResult(Intent.UNCLEAR, null, null, null,
                "We don't have salami. Did you mean Ham & cheese or Tuna?")));

        Interpretation result = new ClaudeOrderInterpreter(MENU, caller).interpret("salami please");

        assertThat(result).isEqualTo(Interpretation.unclear("We don't have salami. Did you mean Ham & cheese or Tuna?"));
    }

    @Test
    void unclearWithoutReplyOrMissingIntentGivesFallbackText() {
        assertThat(new ClaudeOrderInterpreter(MENU, new FakeCaller(Optional.of(
                new AiResult(Intent.UNCLEAR, "", "", "", " ")))).interpret("hm"))
                .isEqualTo(Interpretation.unclear(FALLBACK));
        assertThat(new ClaudeOrderInterpreter(MENU, new FakeCaller(Optional.of(
                new AiResult(null, "Sandwich", "Tuna", "", "")))).interpret("hm"))
                .isEqualTo(Interpretation.unclear(FALLBACK));
    }
}
