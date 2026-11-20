package com.bugfarm.lunch.admin.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredContentBlock;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.anthropic.models.messages.StructuredTextBlock;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Calls Claude through the official SDK, with a hard time limit and no retries. */
public class AnthropicClaudeCaller implements ClaudeCaller {

    private static final Logger log = LoggerFactory.getLogger(AnthropicClaudeCaller.class);

    private final AnthropicClient client;
    private final String model;

    /** Reads ANTHROPIC_API_KEY from the environment. */
    public AnthropicClaudeCaller(String model, Duration timeout) {
        this(AnthropicOkHttpClient.builder().fromEnv().timeout(timeout).maxRetries(0).build(), model);
    }

    AnthropicClaudeCaller(AnthropicClient client, String model) {
        this.client = client;
        this.model = model;
    }

    static StructuredMessageCreateParams<AiResult> request(String model, String systemPrompt, String userText) {
        return MessageCreateParams.builder()
                .model(model)
                .maxTokens(1024L)
                .system(systemPrompt)
                .addUserMessage(userText)
                .outputConfig(StructuredOutputConfig.<AiResult>builder()
                        .format(AiResult.class)
                        .effort(OutputConfig.Effort.LOW)
                        .build())
                .build();
    }

    @Override
    public Optional<AiResult> call(String systemPrompt, String userText) {
        try {
            StructuredMessage<AiResult> message = client.messages().create(request(model, systemPrompt, userText));
            if (message.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
                return Optional.empty();
            }
            return message.content().stream()
                    .map(StructuredContentBlock::text)
                    .flatMap(Optional::stream)
                    .findFirst()
                    .map(StructuredTextBlock::text);
        } catch (RuntimeException e) {
            // Never log the message text or the exception message (it may echo the request).
            log.warn("Claude call failed: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
