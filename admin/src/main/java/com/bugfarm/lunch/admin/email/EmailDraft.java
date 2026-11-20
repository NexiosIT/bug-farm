package com.bugfarm.lunch.admin.email;

import com.fasterxml.jackson.annotation.JsonProperty;

/** The email to the shop. {@code finalDraft} (JSON {@code final}) is true once ordering has closed. */
public record EmailDraft(String to, String subject, String body, @JsonProperty("final") boolean finalDraft) {}
