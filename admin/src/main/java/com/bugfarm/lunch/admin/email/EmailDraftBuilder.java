package com.bugfarm.lunch.admin.email;

import com.bugfarm.lunch.contracts.Order;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Builds the plain-text order email. A pure function of its inputs, so automatic
 * sending can reuse it later. Uses the food type and item text saved with each
 * order, never the current menu.
 */
@Component
public class EmailDraftBuilder {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final Comparator<String> BY_NAME =
            String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());

    private record Dish(String foodType, String item) {}

    public EmailDraft build(List<Order> orders, LocalDate date, String shopEmail, boolean finalDraft) {
        String day = DAY.format(date);
        String subject = "Order Lunch, " + day;
        StringBuilder body = new StringBuilder("Order for ").append(day).append("\n\n");

        if (orders.isEmpty()) {
            body.append("No orders today.");
            return new EmailDraft(shopEmail, subject, body.toString(), finalDraft);
        }

        boolean severalFoodTypes = orders.stream().map(Order::foodType).distinct().count() > 1;
        Map<Dish, Long> counts = orders.stream()
                .collect(Collectors.groupingBy(o -> new Dish(o.foodType(), o.item()), LinkedHashMap::new,
                        Collectors.counting()));
        counts.entrySet().stream()
                .sorted(Map.Entry.<Dish, Long>comparingByValue().reversed()
                        .thenComparing(e -> e.getKey().item(), BY_NAME)
                        .thenComparing(e -> e.getKey().foodType(), BY_NAME))
                .forEach(e -> {
                    body.append(e.getValue()).append(" × ").append(e.getKey().item());
                    if (severalFoodTypes) {
                        body.append(" (").append(e.getKey().foodType()).append(')');
                    }
                    body.append('\n');
                });

        body.append("\nPer person:");
        orders.stream()
                .sorted(Comparator.comparing(Order::displayName, BY_NAME))
                .forEach(o -> {
                    body.append('\n').append(o.displayName()).append(": ").append(o.item());
                    if (!o.notes().isBlank()) {
                        body.append(" (").append(o.notes()).append(')');
                    }
                });

        return new EmailDraft(shopEmail, subject, body.toString(), finalDraft);
    }
}
