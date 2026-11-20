package com.bugfarm.lunch.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class LunchApplicationTest {

    @Autowired
    private Clock clock;

    @Test
    void contextLoads() {
    }

    @Test
    void clockUsesConfiguredZone() {
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Europe/Brussels"));
    }
}
