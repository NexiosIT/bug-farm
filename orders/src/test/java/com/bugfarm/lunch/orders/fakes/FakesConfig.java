package com.bugfarm.lunch.orders.fakes;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Stand-ins for what other parts provide at runtime: the menu and the ordering window (admin)
 * and the Clock (app). The clock is Fri 20 Nov 2026, 09:30 in Brussels.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FakesConfig {

    public static final Instant NOW = Instant.parse("2026-11-20T08:30:00Z");

    @Bean
    FakeMenuCatalog fakeMenuCatalog() {
        return new FakeMenuCatalog();
    }

    @Bean
    FakeOrderingWindow fakeOrderingWindow() {
        return new FakeOrderingWindow();
    }

    @Bean
    Clock clock() {
        return Clock.fixed(NOW, ZoneId.of("Europe/Brussels"));
    }
}
