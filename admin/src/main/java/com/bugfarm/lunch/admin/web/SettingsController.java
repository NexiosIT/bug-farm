package com.bugfarm.lunch.admin.web;

import com.bugfarm.lunch.admin.settings.Settings;
import com.bugfarm.lunch.admin.settings.SettingsRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/settings")
public class SettingsController {

    /** Request and response body. {@code shopEmail} is {@code ""} or a valid address. */
    public record SettingsBody(
            @NotNull(message = "must be HH:mm")
            @Pattern(regexp = TimeFormat.HHMM_PATTERN, message = "must be HH:mm")
            String cutOff,
            @NotNull(message = "must be empty or a valid email address")
            @Email(message = "must be a valid email address")
            String shopEmail) {

        static SettingsBody of(Settings s) {
            return new SettingsBody(TimeFormat.hhmm(s.cutOff()), s.shopEmail());
        }
    }

    private final SettingsRepository settings;

    public SettingsController(SettingsRepository settings) {
        this.settings = settings;
    }

    @GetMapping
    public SettingsBody get() {
        return SettingsBody.of(settings.get());
    }

    @PutMapping
    public SettingsBody update(@Valid @RequestBody SettingsBody body) {
        settings.update(new Settings(TimeFormat.parse(body.cutOff()), body.shopEmail()));
        return SettingsBody.of(settings.get());
    }
}
