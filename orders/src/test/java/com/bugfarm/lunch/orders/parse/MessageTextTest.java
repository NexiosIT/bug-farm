package com.bugfarm.lunch.orders.parse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MessageTextTest {

    @Test
    void mentionAndNbspAreRemoved() {
        assertThat(MessageText.clean("<at>SandwichBot</at>&nbsp;sandwich: tuna")).isEqualTo("sandwich: tuna");
    }

    @Test
    void paragraphTagsStrippedAndAmpersandDecoded() {
        assertThat(MessageText.clean("<p><at>SandwichBot</at> Ham &amp; cheese</p>")).isEqualTo("Ham & cheese");
    }

    @Test
    void surroundingWhitespaceAndNewlinesCollapsed() {
        assertThat(MessageText.clean("  <at>Bot</at>  menu \n")).isEqualTo("menu");
    }

    @Test
    void otherEntitiesDecodedOnceOnly() {
        assertThat(MessageText.clean("&lt;b&gt; &quot;x&quot; it&#39;s &amp;lt;"))
                .isEqualTo("<b> \"x\" it's &lt;");
    }

    @Test
    void nullGivesEmptyText() {
        assertThat(MessageText.clean(null)).isEmpty();
    }
}
