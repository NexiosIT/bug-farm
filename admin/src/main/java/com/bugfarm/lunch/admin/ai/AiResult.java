package com.bugfarm.lunch.admin.ai;

import com.bugfarm.lunch.contracts.Intent;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** What Claude returns for one message. The derived JSON schema uses these descriptions. */
public record AiResult(
        @JsonPropertyDescription("What the person wants: ORDER, CANCEL, MY_ORDER, MENU, HELP or UNCLEAR.")
        Intent intent,
        @JsonPropertyDescription("For ORDER: the food type exactly as spelled on the menu. Otherwise empty.")
        String foodType,
        @JsonPropertyDescription("For ORDER: the item name exactly as spelled on the menu. Otherwise empty.")
        String item,
        @JsonPropertyDescription("For ORDER: extras such as 'no butter'. Empty when there are none.")
        String notes,
        @JsonPropertyDescription("For UNCLEAR: a short friendly reply of at most 2 sentences. Otherwise empty.")
        String reply) {}
