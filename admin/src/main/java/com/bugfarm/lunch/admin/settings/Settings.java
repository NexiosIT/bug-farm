package com.bugfarm.lunch.admin.settings;

import java.time.LocalTime;

/** Cut-off time and shop email ({@code ""} when not set). */
public record Settings(LocalTime cutOff, String shopEmail) {}
