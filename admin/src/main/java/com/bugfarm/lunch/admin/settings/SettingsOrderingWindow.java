package com.bugfarm.lunch.admin.settings;

import com.bugfarm.lunch.contracts.OrderingWindow;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Component;

/** Open Monday to Friday, before the cut-off, in the clock's time zone. */
@Component
public class SettingsOrderingWindow implements OrderingWindow {

    private final SettingsRepository settings;
    private final Clock clock;

    public SettingsOrderingWindow(SettingsRepository settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public boolean isOpen(Instant now) {
        ZonedDateTime local = now.atZone(clock.getZone());
        DayOfWeek day = local.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        return local.toLocalTime().isBefore(cutOff());
    }

    @Override
    public LocalTime cutOff() {
        return settings.get().cutOff();
    }
}
