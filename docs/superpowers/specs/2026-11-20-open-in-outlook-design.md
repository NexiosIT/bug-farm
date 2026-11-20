# Open the order email in Outlook — Design

Date: 2026-11-20
Status: approved 2026-11-20
Size: small change (frontend only)

## Intent

The order manager should be able to send the daily order email to the shop
address in Settings without copying To, Subject and Body by hand. The main
design dropped automatic sending because it needs Microsoft 365 admin consent
or a third-party mail service; this change avoids both by opening the
manager's own mail app with everything filled in. The manager still clicks
Send, from their own mailbox.

## Goals / non-goals

- Goal: one button on the draft email screen opens Outlook with To, Subject
  and Body pre-filled from the existing draft.
- Non-goal: sending from the server, scheduling, or sending without the
  manager clicking Send.
- Anti-goal: anything that needs credentials, secrets or admin consent.

## Approaches

- **`mailto:` link from the frontend** — chosen. No backend change, no
  credentials; opens the default mail app (Outlook on office PCs).
- Server-side sending via Microsoft Graph — needs M365 admin consent; already
  ruled out by the team.
- Server-side sending via SMTP / third-party mail service — needs a mail
  account or service and secrets; already ruled out by the team.

## Design

- `EmailDraftView` gets an **Open in Outlook** button: an `<a>` whose `href` is
  `mailto:<to>?subject=<subject>&body=<body>`, built from the draft the page
  already loads (`/api/admin/email-draft`). Subject and body are encoded with
  `encodeURIComponent` (so line breaks, `&`, `×` and accents survive).
- The **Copy buttons are removed** (decided by the order manager: extra bloat).
  To, Subject and Body stay visible as read-only text so the manager can see
  what will be sent. Known trade-off: classic Outlook truncates `mailto` links
  around 2,000 characters, and there is no longer a copy fallback; a normal
  day's order is far below that.
- No shop email in Settings → the button is disabled (rendered as a disabled
  button, not a link) with a note pointing to Settings.
- Before the cut-off the button still works; the existing "Ordering is still
  open — orders may still change." callout stays.
- Mock handlers (`VITE_MOCK=1`) need no change; they already serve the draft.

## Tests (first)

- The link's `href` has the shop address and correctly encoded subject and
  body (line breaks, `&`, `×`).
- The button is disabled and the Settings note shows when the shop email is
  empty.
- No Copy buttons are rendered; existing Copy tests are removed.

## Left to the builder

- Exact button label styling (use the existing `btn btn-primary` classes) and
  placement next to the heading.
- Wording of the "no shop email" note.
