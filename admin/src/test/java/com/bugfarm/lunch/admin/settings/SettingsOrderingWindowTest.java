package com.bugfarm.lunch.admin.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;

@JdbcTest
@Import(SettingsRepository.class)
class SettingsOrderingWindowTest {

    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");

    @Autowired
    private SettingsRepository settings;

    private SettingsOrderingWindow window;

    @BeforeEach
    void setUp() {
        // The clock only supplies the zone; isOpen gets the instant to check.
        Clock clock = Clock.fixed(Instant.parse("2026-11-18T07:00:00Z"), BRUSSELS);
        window = new SettingsOrderingWindow(settings, clock);
    }

    @Test
    void openAt0959_59OnWednesday() {
        // 2026-11-18 is a Wednesday; Brussels is UTC+1 in November.
        assertThat(window.isOpen(Instant.parse("2026-11-18T08:59:59Z"))).isTrue();
    }

    @Test
    void closedAt1000OnWednesday() {
        assertThat(window.isOpen(Instant.parse("2026-11-18T09:00:00Z"))).isFalse();
    }

    @Test
    void closedOnSaturdayMorning() {
        assertThat(window.isOpen(Instant.parse("2026-11-21T07:00:00Z"))).isFalse();
    }

    @Test
    void usesConfiguredZoneNotUtc() {
        assertThat(window.isOpen(Instant.parse("2026-11-18T08:30:00Z"))).isTrue();
        assertThat(window.isOpen(Instant.parse("2026-11-18T09:30:00Z"))).isFalse();
    }

    @Test
    void followsUpdatedCutOff() {
        settings.update(new Settings(LocalTime.of(11, 0), ""));

        assertThat(window.cutOff()).isEqualTo(LocalTime.of(11, 0));
        assertThat(window.isOpen(Instant.parse("2026-11-18T09:30:00Z"))).isTrue();
        assertThat(window.isOpen(Instant.parse("2026-11-18T10:00:00Z"))).isFalse();
    }
}
