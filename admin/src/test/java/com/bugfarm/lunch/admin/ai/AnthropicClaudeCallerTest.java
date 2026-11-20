package com.bugfarm.lunch.admin.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import org.junit.jupiter.api.Test;

/** Offline checks of the request; no network. */
class AnthropicClaudeCallerTest {

    @Test
    void buildsStructuredRequestFromAiResult() {
        // Building the params derives and locally validates the JSON schema for AiResult.
        StructuredMessageCreateParams<AiResult> params =
                AnthropicClaudeCaller.request("claude-haiku-5-5", "system text", "sandwich please");

        assertThat(params.outputType()).isEqualTo(AiResult.class);
        assertThat(params.rawParams().model().asString()).isEqualTo("claude-haiku-5-5");
        assertThat(params.rawParams().maxTokens()).isEqualTo(1024L);
        assertThat(params.rawParams().messages()).hasSize(1);
    }

    @Test
    void usesLowEffort() {
        // Spec: "Low effort; it's a short extraction" — keeps the call well inside the 3.5 s limit.
        StructuredMessageCreateParams<AiResult> params =
                AnthropicClaudeCaller.request("claude-haiku-5-5", "system text", "sandwich please");

        assertThat(params.rawParams().outputConfig()).get()
                .extracting(c -> c.effort().orElse(null))
                .isEqualTo(OutputConfig.Effort.LOW);
    }
}
