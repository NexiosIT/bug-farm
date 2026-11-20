package com.bugfarm.lunch.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InterpretationTest {

    @Test
    void orderFactorySetsIntentAndFields() {
        Interpretation i = Interpretation.order("Sandwich", "Tuna", "no butter");
        assertThat(i.intent()).isEqualTo(Intent.ORDER);
        assertThat(i.foodType()).isEqualTo("Sandwich");
        assertThat(i.item()).isEqualTo("Tuna");
        assertThat(i.notes()).isEqualTo("no butter");
    }

    @Test
    void ofLeavesOtherFieldsEmptyNotNull() {
        Interpretation i = Interpretation.of(Intent.HELP);
        assertThat(i.intent()).isEqualTo(Intent.HELP);
        assertThat(i.foodType()).isEmpty();
        assertThat(i.item()).isEmpty();
        assertThat(i.notes()).isEmpty();
    }

    @Test
    void nullStringsBecomeEmpty() {
        Interpretation i = new Interpretation(Intent.ORDER, null, null, null);
        assertThat(i.foodType()).isEqualTo("");
        assertThat(i.item()).isEqualTo("");
        assertThat(i.notes()).isEqualTo("");
    }

    @Test
    void intentsAreTheCommandsAndOrderOnly() {
        assertThat(Intent.values())
                .containsExactly(Intent.ORDER, Intent.CANCEL, Intent.MY_ORDER, Intent.MENU, Intent.HELP);
    }
}
