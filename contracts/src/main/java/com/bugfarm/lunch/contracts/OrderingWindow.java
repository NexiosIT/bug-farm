package com.bugfarm.lunch.contracts;

import java.time.Instant;
import java.time.LocalTime;

public interface OrderingWindow {
    /** True while ordering is open at the given instant. */
    boolean isOpen(Instant now);

    /** The daily cut-off time. */
    LocalTime cutOff();
}
