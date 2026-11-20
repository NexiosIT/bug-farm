package com.bugfarm.lunch.orders.suggest;

import static org.assertj.core.api.Assertions.assertThat;

import com.bugfarm.lunch.contracts.MenuItem;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenChoicesTest {

    private static final MenuItem TUNA = new MenuItem(2, "Sandwich", "Tuna");
    private static final MenuItem SMOS = new MenuItem(7, "Sandwich", "Smos");

    private final TestClock clock = new TestClock(Instant.parse("2026-11-20T08:30:00Z"));
    private final OpenChoices choices = new OpenChoices(clock);

    @Test
    void offerThenFind() {
        choices.offer("jan", List.of(TUNA), "no butter");

        assertThat(choices.find("jan")).hasValueSatisfying(c -> {
            assertThat(c.options()).containsExactly(TUNA);
            assertThat(c.notes()).isEqualTo("no butter");
            assertThat(c.offeredAt()).isEqualTo(Instant.parse("2026-11-20T08:30:00Z"));
        });
    }

    @Test
    void otherPersonHasNothing() {
        choices.offer("jan", List.of(TUNA), "");

        assertThat(choices.find("piet")).isEmpty();
    }

    @Test
    void newOfferReplacesOld() {
        choices.offer("jan", List.of(TUNA), "");
        choices.offer("jan", List.of(SMOS), "");

        assertThat(choices.find("jan")).hasValueSatisfying(c -> assertThat(c.options()).containsExactly(SMOS));
    }

    @Test
    void clearRemoves() {
        choices.offer("jan", List.of(TUNA), "");
        choices.clear("jan");

        assertThat(choices.find("jan")).isEmpty();
    }

    @Test
    void expiresAfterFifteenMinutes() {
        choices.offer("jan", List.of(TUNA), "");

        clock.advance(Duration.ofMinutes(14).plusSeconds(59));
        assertThat(choices.find("jan")).isPresent();

        clock.advance(Duration.ofSeconds(1));
        assertThat(choices.find("jan")).isEmpty();
    }

    @Test
    void expiresAtMidnight() {
        clock.set(Instant.parse("2026-11-20T22:55:00Z")); // 23:55 in Brussels
        choices.offer("jan", List.of(TUNA), "");

        clock.set(Instant.parse("2026-11-20T23:05:00Z")); // 00:05 the next day
        assertThat(choices.find("jan")).isEmpty();
    }

    /** A clock in Brussels whose time the test sets. */
    private static final class TestClock extends Clock {
        private Instant now;

        TestClock(Instant start) {
            this.now = start;
        }

        void set(Instant instant) {
            now = instant;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Europe/Brussels");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
