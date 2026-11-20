package com.bugfarm.lunch.orders;

import com.bugfarm.lunch.contracts.Interpretation;
import com.bugfarm.lunch.contracts.MenuCatalog;
import com.bugfarm.lunch.contracts.MenuItem;
import com.bugfarm.lunch.contracts.Order;
import com.bugfarm.lunch.contracts.OrderInterpreter;
import com.bugfarm.lunch.contracts.OrderingWindow;
import com.bugfarm.lunch.orders.parse.CommandParser;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Turns one cleaned chat message from one person into a reply, saving or cancelling their order. */
@Component
public class OrderHandler {

    static final String CLOSED = "Ordering is closed for today.";
    static final String AI_FALLBACK = "Sorry, I couldn't read that. Try e.g. sandwich: ham & cheese, no butter.";
    static final int MAX_NOTES = 500;

    private static final Logger log = LoggerFactory.getLogger(OrderHandler.class);

    private static final String HELP = String.join("\n\n",
            "Mention me with one of these:",
            "- menu: today's menu",
            "- {food type}: {item}, {notes}: order for today, e.g. sandwich: ham & cheese, no butter."
                    + " A new order replaces your earlier one.",
            "- my order: your order for today",
            "- cancel: cancel your order for today",
            "- help: this text",
            "Free-text messages are read by an AI service, so please don't include other people's details.");

    private final CommandParser parser;
    private final OrderInterpreter interpreter;
    private final JdbcOrderService orders;
    private final MenuCatalog menu;
    private final OrderingWindow window;
    private final Clock clock;

    public OrderHandler(CommandParser parser, OrderInterpreter interpreter, JdbcOrderService orders,
                        MenuCatalog menu, OrderingWindow window, Clock clock) {
        this.parser = parser;
        this.interpreter = interpreter;
        this.orders = orders;
        this.menu = menu;
        this.window = window;
        this.clock = clock;
    }

    public String handle(String teamsUserId, String displayName, String cleanedText) {
        Interpretation meaning = parser.parse(cleanedText).orElseGet(() -> interpret(cleanedText));
        LocalDate today = LocalDate.now(clock);
        return switch (meaning.intent()) {
            case HELP -> HELP;
            case MENU -> menuText();
            case MY_ORDER -> orders.findFor(teamsUserId, today)
                    .map(OrderHandler::describe)
                    .orElse("You have no order yet today.");
            case UNCLEAR -> meaning.reply().isBlank() ? AI_FALLBACK : meaning.reply();
            case CANCEL -> !isOpen() ? CLOSED
                    : orders.cancel(teamsUserId, today) ? "Your order for today is cancelled."
                    : "You have no order to cancel.";
            case ORDER -> !isOpen() ? CLOSED : order(teamsUserId, displayName, meaning, today);
        };
    }

    private Interpretation interpret(String text) {
        try {
            Interpretation result = interpreter.interpret(text);
            return result == null || result.intent() == null ? Interpretation.unclear(AI_FALLBACK) : result;
        } catch (RuntimeException e) {
            log.warn("Order interpreter failed: {}", e.getClass().getName());
            return Interpretation.unclear(AI_FALLBACK);
        }
    }

    private boolean isOpen() {
        return window.isOpen(clock.instant());
    }

    private String order(String teamsUserId, String displayName, Interpretation meaning, LocalDate today) {
        Optional<MenuItem> found = menu.find(meaning.foodType(), meaning.item());
        if (found.isEmpty()) {
            List<MenuItem> ofType = menu.itemsFor(meaning.foodType());
            if (ofType.isEmpty()) {
                return "We don't have " + meaning.foodType().trim() + ". We do: "
                        + String.join(", ", menu.foodTypes()) + ".";
            }
            return "We don't have " + meaning.item().trim() + ". On the " + ofType.get(0).foodType()
                    + " menu: " + names(ofType) + ".";
        }
        String notes = meaning.notes().trim();
        if (notes.length() > MAX_NOTES) {
            return "Your notes are too long (max " + MAX_NOTES + " characters).";
        }
        MenuItem item = found.get();
        Order saved = orders.save(teamsUserId, displayName, item.foodType(), item.name(), notes,
                today, clock.instant());
        return "Got it, " + describe(saved);
    }

    private String menuText() {
        String lines = menu.foodTypes().stream()
                .map(type -> type + ": " + names(menu.itemsFor(type)))
                .collect(Collectors.joining("\n\n"));
        return lines.isEmpty() ? "Today's menu:\n\nThere is nothing on the menu yet." : "Today's menu:\n\n" + lines;
    }

    private static String names(List<MenuItem> items) {
        return items.stream().map(MenuItem::name).collect(Collectors.joining(", "));
    }

    /** {displayName}: {FoodType}, {Item} plus " ({notes})" when there are notes. */
    private static String describe(Order order) {
        String text = order.displayName() + ": " + order.foodType() + ", " + order.item();
        return order.notes().isEmpty() ? text : text + " (" + order.notes() + ")";
    }
}
