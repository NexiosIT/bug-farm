package com.bugfarm.lunch.orders.parse;

import static org.assertj.core.api.Assertions.assertThat;

import com.bugfarm.lunch.contracts.Intent;
import com.bugfarm.lunch.contracts.Interpretation;
import org.junit.jupiter.api.Test;

class CommandParserTest {

    private final CommandParser parser = new CommandParser();

    @Test
    void orderWithNotes() {
        assertThat(parser.parse("Sandwich: Ham & cheese, no butter"))
                .contains(Interpretation.order("Sandwich", "Ham & cheese", "no butter"));
    }

    @Test
    void orderWithoutSpacesHasEmptyNotes() {
        assertThat(parser.parse("sandwich:tuna")).contains(Interpretation.order("sandwich", "tuna", ""));
    }

    @Test
    void myOrderIsCaseInsensitive() {
        assertThat(parser.parse("MY ORDER")).contains(Interpretation.of(Intent.MY_ORDER));
    }

    @Test
    void fixedCommands() {
        assertThat(parser.parse("cancel")).contains(Interpretation.of(Intent.CANCEL));
        assertThat(parser.parse("Menu")).contains(Interpretation.of(Intent.MENU));
        assertThat(parser.parse("help")).contains(Interpretation.of(Intent.HELP));
    }

    @Test
    void freeTextGoesToAi() {
        assertThat(parser.parse("can I get a ham cheese pls")).isEmpty();
        assertThat(parser.parse("cancel please")).isEmpty();
    }

    @Test
    void missingItemGoesToAi() {
        assertThat(parser.parse("sandwich:")).isEmpty();
        assertThat(parser.parse("sandwich:   , no butter")).isEmpty();
        assertThat(parser.parse(" : tuna")).isEmpty();
    }
}
