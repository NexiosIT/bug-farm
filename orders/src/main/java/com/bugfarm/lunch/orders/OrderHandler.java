package com.bugfarm.lunch.orders;

import com.bugfarm.lunch.contracts.Interpretation;
import com.bugfarm.lunch.contracts.MenuCatalog;
import com.bugfarm.lunch.contracts.MenuItem;
import com.bugfarm.lunch.contracts.Order;
import com.bugfarm.lunch.contracts.OrderingWindow;
import com.bugfarm.lunch.orders.parse.CommandParser;
import com.bugfarm.lunch.orders.suggest.MenuSuggester;
import com.bugfarm.lunch.orders.suggest.OpenChoice;
import com.bugfarm.lunch.orders.suggest.OpenChoices;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

/**
 * Turns one cleaned chat message from one person into a reply, saving or cancelling their order.
 * Order of checks: a number answering open options, then the strict format, then menu suggestions.
 */
@Component
public class OrderHandler {

    static final String CLOSED = "Ordering is closed for today.";
    static final String NO_OPEN_QUESTION =
            "I have no open question for you. Mention me with e.g. sandwich: ham & cheese.";
    static final String NO_LONGER_ON_MENU = "That item is no longer on the menu.";
    static final String EMPTY_MENU = "There is nothing on the menu yet.";
    static final int MAX_NOTES = 500;

    private static final Pattern NUMBER = Pattern.compile("^#?\\s*(\\d)$");

    private static final String HELP = String.join("\n\n",
            "Mention me with one of these:",
            "- menu: today's menu",
            "- {food type}: {item}, {notes}: order for today, e.g. sandwich: ham & cheese, no butter."
                    + " A new order replaces your earlier one.",
            "- my order: your order for today",
            "- cancel: cancel your order for today",
            "- help: this text",
            "Anything else: I suggest the three closest menu items and you answer with 1, 2 or 3.");

    private final CommandParser parser;
    private final MenuSuggester suggester;
    private final OpenChoices choices;
    private final JdbcOrderService orders;
    private final MenuCatalog menu;
    private final OrderingWindow window;
    private final Clock clock;

    public OrderHandler(CommandParser parser, MenuSuggester suggester, OpenChoices choices, JdbcOrderService orders,
                        MenuCatalog menu, OrderingWindow window, Clock clock) {
        this.parser = parser;
        this.suggester = suggester;
        this.choices = choices;
        this.orders = orders;
        this.menu = menu;
        this.window = window;
        this.clock = clock;
    }

    public String handle(String teamsUserId, String displayName, String cleanedText) {
        String text = cleanedText == null ? "" : cleanedText.trim();
        Matcher number = NUMBER.matcher(text);
        if (number.matches()) {
            return answer(teamsUserId, displayName, Integer.parseInt(number.group(1)));
        }
        Optional<Interpretation> parsed = parser.parse(text);
        choices.clear(teamsUserId);
        if (parsed.isEmpty()) {
            return suggest(teamsUserId, text);
        }
        Interpretation meaning = parsed.get();
        LocalDate today = LocalDate.now(clock);
        return switch (meaning.intent()) {
            case HELP -> HELP;
            case MENU -> menuText();
            case MY_ORDER -> orders.findFor(teamsUserId, today)
                    .map(OrderHandler::describe)
                    .orElse("You have no order yet today.");
            case UNCLEAR -> meaning.reply();
            case CANCEL -> !isOpen() ? CLOSED
                    : orders.cancel(teamsUserId, today) ? "Your order for today is cancelled."
                    : "You have no order to cancel.";
            case ORDER -> !isOpen() ? CLOSED : order(teamsUserId, displayName, meaning, today);
        };
    }

    /** A number answering the options this person was offered. */
    private String answer(String teamsUserId, String displayName, int number) {
        Optional<OpenChoice> open = choices.find(teamsUserId);
        if (open.isEmpty()) {
            return NO_OPEN_QUESTION;
        }
        List<MenuItem> options = open.get().options();
        if (number < 1 || number > options.size()) {
            return "Please answer " + numbers(options.size()) + ".";
        }
        choices.clear(teamsUserId);
        if (!isOpen()) {
            return CLOSED;
        }
        MenuItem picked = options.get(number - 1);
        if (menu.find(picked.foodType(), picked.name()).isEmpty()) {
            return NO_LONGER_ON_MENU;
        }
        return order(teamsUserId, displayName,
                Interpretation.order(picked.foodType(), picked.name(), open.get().notes()), LocalDate.now(clock));
    }

    /** The three menu items most likely meant; text after the first comma is kept as notes. */
    private String suggest(String teamsUserId, String text) {
        if (!isOpen()) {
            return CLOSED;
        }
        List<MenuItem> fullMenu = menu.foodTypes().stream()
                .flatMap(type -> menu.itemsFor(type).stream())
                .toList();
        if (fullMenu.isEmpty()) {
            return EMPTY_MENU;
        }
        int comma = text.indexOf(',');
        String words = comma < 0 ? text : text.substring(0, comma);
        String notes = comma < 0 ? "" : text.substring(comma + 1).trim();
        List<MenuItem> options = suggester.suggest(words, fullMenu);
        choices.offer(teamsUserId, options, notes);
        String lines = IntStream.range(0, options.size())
                .mapToObj(i -> (i + 1) + ". " + options.get(i).foodType() + ", " + options.get(i).name())
                .collect(Collectors.joining("\n\n"));
        return "Did you mean:\n\n" + lines + "\n\nReply with @SandwichBot " + numbers(options.size()) + ".";
    }

    /** "1", "1 or 2", "1, 2 or 3". */
    private static String numbers(int count) {
        if (count == 1) {
            return "1";
        }
        String first = IntStream.rangeClosed(1, count - 1).mapToObj(Integer::toString)
                .collect(Collectors.joining(", "));
        return first + " or " + count;
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
