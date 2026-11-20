# Menu suggestions instead of the Claude fallback — design

Date: 2026-11-20 · Asked for by: Robin · Replaces the AI fallback in
`2026-11-20-food-ordering-design.md` (Part 1/Part 2: `OrderInterpreter` with Claude Haiku).

## Why

The bot only understands the strict format (`sandwich: tuna, no butter`) and a few
commands. Everything else went to Claude, which needs an Anthropic API key, credit and
sends message text to an outside service. Without a key every free-text message gets
"Sorry, I couldn't read that", which is what people hit in the first live Teams test
("Brie", "smos aub", "Ham & cheese"). Robin decided to drop Claude and help people pick
from the menu instead.

## What the person sees

```
@SandwichBot brie, no butter
→ Did you mean:
  1. Sandwich, Brie & honey
  2. Sandwich, Mozzarella & tomato
  3. Wrap, Falafel
  Reply with @SandwichBot 1, 2 or 3.
@SandwichBot 1
→ Got it, Robin: Sandwich, Brie & honey (no butter)
```

## Goals

- A message the strict parser doesn't recognise gets the **three menu items most
  likely meant** (fewer if the menu has fewer), numbered, and a request to answer with
  the number.
- Answering `1`, `2` or `3` orders that item for today, with the same rules as a strict
  order: cut-off and weekend check, one order per person per day (replaces), notes limit,
  confirmation text `Got it, {displayName}: {FoodType}, {Item}` plus ` ({notes})`.
- **Notes:** text after the first comma of the unrecognised message becomes the order's
  notes (same rule as the strict format). No comma → no notes.
- Remove Claude completely: no Anthropic dependency, no API key, no message text leaves
  the app.

## Non-goals

- Changing the strict format, `menu`, `my order`, `cancel`, `help` (apart from removing
  the "read by an AI service" line from the help text).
- Changing the reply to a strict order with an unknown item (`sandwich: salami` → "We
  don't have salami. On the Sandwich menu: …"). It already lists the options.
- Surviving restarts or several app instances (see Approaches).
- Understanding sentences ("two tunas for me and Jan") — this is matching, not reading.

## Anti-goals

- Never save an order the person didn't confirm by number or by the strict format.
- Never offer or save an item that is not on the menu at the moment of saving.

## Behaviour, in order of checks

1. **Number answer.** The cleaned text is exactly `1`, `2` or `3` (optionally `#2`,
   surrounding spaces ignored):
   - the person has open options, not older than 15 minutes, from today → order that
     option (cut-off check first; if the item has since been removed from the menu:
     "That item is no longer on the menu." and the options are dropped);
   - the number is higher than the number of options offered → "Please answer 1, 2 or 3."
     style message naming the valid numbers; options stay open;
   - no open options → "I have no open question for you. Mention me with e.g.
     sandwich: ham & cheese."
   Any answer that leads to a save or a refusal clears the person's options.
2. **Strict parser** (unchanged). A recognised command or strict order clears that
   person's open options.
3. **Not recognised:**
   - ordering closed (after cut-off or weekend) → "Ordering is closed for today." —
     no options offered;
   - empty menu → "There is nothing on the menu yet.";
   - otherwise → the top three suggestions, stored as that person's open options
     (replacing earlier ones), and the reply above.

## Matching (scoring)

Score every menu item against the words before the first comma. Both sides are
lower-cased, `&` is treated as `and`, punctuation is dropped, text split into words.
For each typed word, the best match against the item's name words counts most, then a
match against the food type's words; a typed word that matches nothing adds nothing
(so "can I get a", "pls", "aub" do no harm). A word matches when it is:

- equal (best), or
- a prefix of at least three letters (`moz` → Mozzarella), or
- a small typo: edit distance 1 for words of 4–6 letters, 2 for 7 or more (`brei` → Brie).

Highest total score first; ties keep menu order (food type, then item, as
`MenuCatalog` returns them). The exact weights are the builder's call, tuned with the
test cases below.

Cases the tests must cover: `Brie` → Brie & honey first · `smos aub` → Smos ·
`ham and cheese` and `HAM & CHEESE` → Ham & cheese · `moz` → Mozzarella & tomato ·
`wrap falafel` → Wrap, Falafel · `brei` → Brie & honey · a menu with one or two items
→ that many options · nonsense (`hello`) → still three options, in menu order.

## Approaches considered

1. **Own word matching in plain Java, open options in memory** — chosen. Simple, no new
   dependency, fast enough for Teams' 5 s limit. A restart forgets open questions; that
   only affects someone mid-choice.
2. Same matching, open options in a database table — survives restarts and works with
   several instances (e.g. later on Azure), but adds a table, migration and clean-up for
   a question that lives about a minute. Easy to switch to later: only the store changes.
3. A fuzzy-matching library (Apache Commons Text) — ready-made typo scores, but a new
   dependency and harder to tune for "food type + item" names.

## What changes where

- `orders/`: `OrderHandler` uses a new suggester and an in-memory store of open options
  (keyed on the Teams `aadObjectId`, `Clock`-based expiry) instead of `OrderInterpreter`;
  help text without the AI line; `CommandParser` comment no longer mentions the AI.
- `contracts/`: remove `OrderInterpreter`; remove `Intent.UNCLEAR` and
  `Interpretation.reply` / `unclear(...)` if nothing uses them any more (builder's call).
- `admin/`: delete `admin/ai/` and its tests; remove the `anthropic-java` dependency.
- `app/`: `WiringTest` no longer expects `ClaudeOrderInterpreter`.
- `README.md`: drop `ANTHROPIC_API_KEY`, the live Claude test and the "free text is read
  by Claude" lines; describe the numbered suggestions. Same for Docker files if they
  pass the key.
- Specs/plan: a line in the original design pointing to this document.

## Left to the builder

Scoring weights; exact wording of the error replies above (sentence case, no emoji,
keep them short); class names; whether the store sweeps expired entries or drops them
on read.
