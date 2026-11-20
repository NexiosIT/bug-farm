package com.bugfarm.lunch.orders.suggest;

import com.bugfarm.lunch.contracts.MenuItem;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Remembers, per Teams user, the options they were last offered, so they can answer with a
 * number. Kept in memory only: a restart forgets open questions. Options expire after
 * {@link #TTL} and at the end of the day they were offered.
 */
@Component
public class OpenChoices {

    static final Duration TTL = Duration.ofMinutes(15);

    private final Map<String, OpenChoice> byUser = new ConcurrentHashMap<>();
    private final Clock clock;

    public OpenChoices(Clock clock) {
        this.clock = clock;
    }

    /** Replaces any earlier offer for this person. */
    public void offer(String teamsUserId, List<MenuItem> options, String notes) {
        byUser.put(teamsUserId, new OpenChoice(options, notes, clock.instant()));
    }

    /** The person's open options, unless there are none or they have expired. */
    public Optional<OpenChoice> find(String teamsUserId) {
        OpenChoice choice = byUser.get(teamsUserId);
        if (choice == null) {
            return Optional.empty();
        }
        if (expired(choice)) {
            byUser.remove(teamsUserId, choice);
            return Optional.empty();
        }
        return Optional.of(choice);
    }

    public void clear(String teamsUserId) {
        byUser.remove(teamsUserId);
    }

    private boolean expired(OpenChoice choice) {
        boolean tooOld = !clock.instant().isBefore(choice.offeredAt().plus(TTL));
        LocalDate offeredOn = LocalDate.ofInstant(choice.offeredAt(), clock.getZone());
        return tooOld || offeredOn.isBefore(LocalDate.now(clock));
    }
}
