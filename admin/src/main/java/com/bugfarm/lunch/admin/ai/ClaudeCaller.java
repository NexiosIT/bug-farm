package com.bugfarm.lunch.admin.ai;

import java.util.Optional;

/** One call to Claude. Empty means a refusal, a timeout or an error. */
public interface ClaudeCaller {
    Optional<AiResult> call(String systemPrompt, String userText);
}
