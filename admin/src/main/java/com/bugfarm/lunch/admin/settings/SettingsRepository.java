package com.bugfarm.lunch.admin.settings;

import java.time.LocalTime;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The single settings row (id = 1). */
@Repository
public class SettingsRepository {

    private final JdbcClient jdbc;

    public SettingsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Settings get() {
        return jdbc.sql("SELECT cut_off, shop_email FROM settings WHERE id = 1")
                .query((rs, n) -> new Settings(rs.getObject("cut_off", LocalTime.class), rs.getString("shop_email")))
                .single();
    }

    public void update(Settings settings) {
        jdbc.sql("UPDATE settings SET cut_off = :cutOff, shop_email = :email WHERE id = 1")
                .param("cutOff", settings.cutOff())
                .param("email", settings.shopEmail() == null ? "" : settings.shopEmail())
                .update();
    }
}
