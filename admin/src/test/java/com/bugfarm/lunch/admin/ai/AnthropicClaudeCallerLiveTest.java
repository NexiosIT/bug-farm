package com.bugfarm.lunch.admin.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.bugfarm.lunch.contracts.Intent;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Calls the real API. Runs only with ANTHROPIC_API_KEY set; select it with -Dgroups=live. */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicClaudeCallerLiveTest {

    @Test
    void realOrderIsUnderstood() {
        ClaudeCaller caller = new AnthropicClaudeCaller("claude-haiku-5-5", Duration.ofMillis(3500));
        String system = InterpreterPrompt.system(Map.of("Sandwich", List.of("Chicken curry", "Ham & cheese", "Tuna")));

        long start = System.nanoTime();
        Optional<AiResult> result = caller.call(system, "can I get a ham cheese no butter pls");
        Duration took = Duration.ofNanos(System.nanoTime() - start);

        assertThat(result).isPresent();
        assertThat(result.get().intent()).isEqualTo(Intent.ORDER);
        assertThat(result.get().foodType()).isEqualTo("Sandwich");
        assertThat(result.get().item()).isEqualTo("Ham & cheese");
        assertThat(result.get().notes()).containsIgnoringCase("butter");
        assertThat(took).isLessThan(Duration.ofMillis(3500));
    }
}
