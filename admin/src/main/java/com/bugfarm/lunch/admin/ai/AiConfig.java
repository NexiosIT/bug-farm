package com.bugfarm.lunch.admin.ai;

import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    /**
     * The real caller when ANTHROPIC_API_KEY is set. Without a key every call is empty,
     * so local runs still work and free text gets the fallback reply.
     */
    @Bean
    public ClaudeCaller claudeCaller(@Value("${ANTHROPIC_API_KEY:}") String apiKey,
                                     @Value("${lunch.ai.model:claude-haiku-5-5}") String model,
                                     @Value("${lunch.ai.timeout:3500ms}") Duration timeout) {
        if (apiKey.isBlank()) {
            log.info("ANTHROPIC_API_KEY is not set: free-text messages get the fallback reply");
            return (systemPrompt, userText) -> Optional.empty();
        }
        return new AnthropicClaudeCaller(model, timeout);
    }
}
