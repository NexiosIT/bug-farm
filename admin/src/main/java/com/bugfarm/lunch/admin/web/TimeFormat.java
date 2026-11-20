package com.bugfarm.lunch.admin.web;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

final class TimeFormat {

    static final String HHMM_PATTERN = "^([01]\\d|2[0-3]):[0-5]\\d$";

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private TimeFormat() {
    }

    static String hhmm(LocalTime time) {
        return HHMM.format(time);
    }

    static LocalTime parse(String hhmm) {
        return LocalTime.parse(hhmm, HHMM);
    }
}
