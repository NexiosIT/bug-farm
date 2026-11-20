package com.bugfarm.lunch.admin.email;

import com.bugfarm.lunch.admin.settings.SettingsRepository;
import com.bugfarm.lunch.contracts.OrderService;
import com.bugfarm.lunch.contracts.OrderingWindow;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EmailDraftController {

    private final EmailDraftBuilder builder;
    private final OrderService orders;
    private final OrderingWindow window;
    private final SettingsRepository settings;
    private final Clock clock;

    public EmailDraftController(EmailDraftBuilder builder, OrderService orders, OrderingWindow window,
                                SettingsRepository settings, Clock clock) {
        this.builder = builder;
        this.orders = orders;
        this.window = window;
        this.settings = settings;
        this.clock = clock;
    }

    @GetMapping("/api/admin/email-draft")
    public EmailDraft draft() {
        LocalDate today = LocalDate.now(clock);
        return builder.build(orders.ordersFor(today), today, settings.get().shopEmail(),
                !window.isOpen(clock.instant()));
    }
}
