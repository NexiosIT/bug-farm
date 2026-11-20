package com.bugfarm.lunch.admin.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class DefaultSandwichesMigrationTest {

    private Flyway flyway;
    private JdbcClient jdbc;

    @BeforeEach
    void migrateTo201() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/admin")
                .target("201")
                .load();
        flyway.migrate();
        jdbc = JdbcClient.create(dataSource);
    }

    @Test
    void skipsItemsThatAlreadyExist() {
        jdbc.sql("INSERT INTO menu_item (food_type_id, name) "
                + "SELECT id, 'Ham & cheese' FROM food_type WHERE name = 'Sandwich'").update();

        migrateToLatest();

        assertThat(names("Sandwich"))
                .containsExactlyInAnyOrder("Cheese", "Chicken curry", "Ham & cheese", "Martino", "Tuna");
    }

    @Test
    void addsNothingWhenSandwichIsGone() {
        jdbc.sql("UPDATE food_type SET name = 'Broodjes' WHERE name = 'Sandwich'").update();

        migrateToLatest();

        assertThat(count("SELECT COUNT(*) FROM menu_item")).isZero();
    }

    private void migrateToLatest() {
        Flyway.configure()
                .configuration(flyway.getConfiguration())
                .target("latest")
                .load()
                .migrate();
    }

    private List<String> names(String foodType) {
        return jdbc.sql("SELECT m.name FROM menu_item m JOIN food_type t ON t.id = m.food_type_id "
                        + "WHERE t.name = :type")
                .param("type", foodType)
                .query(String.class)
                .list();
    }

    private int count(String sql) {
        return jdbc.sql(sql).query(Integer.class).single();
    }
}
