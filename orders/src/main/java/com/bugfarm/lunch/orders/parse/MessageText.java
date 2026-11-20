package com.bugfarm.lunch.orders.parse;

import java.util.regex.Pattern;

/** Turns the HTML text of a Teams message into the plain text the person typed. */
public final class MessageText {

    private static final Pattern MENTION = Pattern.compile("<at>.*?</at>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private MessageText() {
    }

    /**
     * Removes {@code <at>…</at>} mentions with their content, strips other tags, decodes
     * {@code &nbsp; &amp; &lt; &gt; &quot; &#39;}, collapses whitespace and trims.
     */
    public static String clean(String teamsHtmlText) {
        if (teamsHtmlText == null) {
            return "";
        }
        String text = MENTION.matcher(teamsHtmlText).replaceAll(" ");
        text = TAG.matcher(text).replaceAll(" ");
        text = text.replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&"); // last, so "&amp;lt;" becomes "&lt;" and not "<"
        return WHITESPACE.matcher(text).replaceAll(" ").trim();
    }
}
