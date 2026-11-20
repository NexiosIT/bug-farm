package com.bugfarm.lunch.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.bugfarm.lunch.admin.settings.Settings;
import com.bugfarm.lunch.admin.settings.SettingsRepository;
import com.bugfarm.lunch.contracts.MenuCatalog;
import com.bugfarm.lunch.contracts.MenuItem;
import com.bugfarm.lunch.contracts.OrderService;
import java.time.LocalDate;
import java.time.LocalTime;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The app on a real PostgreSQL, as in Docker and when hosted. Skipped when Docker isn't available. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private Flyway flyway;

    @Autowired
    private MenuCatalog menuCatalog;

    @Autowired
    private SettingsRepository settingsRepository;

    @Autowired
    private OrderService orderService;

    @Test
    void allMigrationsApplyOnPostgres() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("202");
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void menuAndSettingsWorkOnPostgres() {
        assertThat(menuCatalog.find(" SANDWICH ", "ham & CHEESE")).map(MenuItem::name).contains("Ham & cheese");

        settingsRepository.update(new Settings(LocalTime.of(10, 30), "orders@shop.example"));
        assertThat(settingsRepository.get()).isEqualTo(new Settings(LocalTime.of(10, 30), "orders@shop.example"));
    }

    @Test
    void ordersTodayIsEmptyOnFreshDatabase() {
        assertThat(orderService.ordersFor(LocalDate.of(2026, 11, 20))).isEmpty();
    }
}
