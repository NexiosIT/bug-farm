package com.bugfarm.lunch.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InterpretationTest {

    @Test
    void orderFactorySetsIntentAndFields() {
        Interpretation i = Interpretation.order("Sandwich", "Tuna", "");
        assertThat(i.intent()).isEqualTo(Intent.ORDER);
        assertThat(i.foodType()).isEqualTo("Sandwich");
        assertThat(i.item()).isEqualTo("Tuna");
        assertThat(i.reply()).isEmpty();
    }

    @Test
    void unclearCarriesReplyAndBlankItem() {
        Interpretation i = Interpretation.unclear("x");
        assertThat(i.intent()).isEqualTo(Intent.UNCLEAR);
        assertThat(i.reply()).isEqualTo("x");
        assertThat(i.item()).isEqualTo("");
    }

    @Test
    void ofLeavesOtherFieldsEmptyNotNull() {
        Interpretation i = Interpretation.of(Intent.HELP);
        assertThat(i.intent()).isEqualTo(Intent.HELP);
        assertThat(i.foodType()).isEmpty();
        assertThat(i.item()).isEmpty();
        assertThat(i.notes()).isEmpty();
        assertThat(i.reply()).isEmpty();
    }

    @Test
    void nullStringsBecomeEmpty() {
        Interpretation i = new Interpretation(Intent.ORDER, null, null, null, null);
        assertThat(i.foodType()).isEqualTo("");
        assertThat(i.item()).isEqualTo("");
        assertThat(i.notes()).isEqualTo("");
        assertThat(i.reply()).isEqualTo("");
    }
}
