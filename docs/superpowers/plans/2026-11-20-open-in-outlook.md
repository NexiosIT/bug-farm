# Open in Outlook Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the three Copy buttons on the draft email screen with one "Open in Outlook" button that opens the manager's mail app with To, Subject and Body filled in.

**Architecture:** Frontend only. A pure helper builds a `mailto:` URL from the `EmailDraft` the view already loads; `EmailDraftView` renders it as a link styled as a button, or a disabled button when no shop email is set. No backend or mock changes.

**Tech Stack:** React + TypeScript, Vitest, Testing Library, MSW (existing).

**Spec:** `docs/superpowers/specs/2026-11-20-open-in-outlook-design.md`

## Global Constraints

- No backend change, no credentials, no new dependencies.
- Copy buttons and all clipboard code are removed.
- Existing callout text stays exactly: `Ordering is still open — orders may still change.`
- Tests first. Run frontend tests with `npm --prefix frontend test -- --run`.

## Review Focus

1. Shop email empty (or whitespace only) → button disabled with a note, no `mailto:` link rendered. Tested in Task 1.
2. Body line breaks must arrive as line breaks in Outlook → encoded as `%0D%0A` (RFC 6068), not bare `%0A`. Tested in Task 1.
3. `&`, `?`, `#`, `×` and accents in subject/body must not cut off or corrupt the URL → `encodeURIComponent`. Tested in Task 1.
4. Address is put into the URL as-is (it was validated by Settings); `@` must not become `%40`. Tested in Task 1.
5. Very long bodies (> ~2,000 chars) may be truncated by classic Outlook. Accepted trade-off in the spec; no handling, no test.

---

### Task 1: Open in Outlook button

**Files:**
- Modify: `frontend/src/EmailDraftView.tsx`
- Test: `frontend/src/EmailDraftView.test.tsx`

**Interfaces:**
- Consumes: `EmailDraft` from `frontend/src/api.ts` (`{ to, subject, body, final }`).
- Produces: `export function mailtoHref(draft: EmailDraft): string` in `EmailDraftView.tsx`.

- [ ] **Step 1: Write the failing tests**

In `EmailDraftView.test.tsx`: delete `copyBodyWritesBodyToClipboard` (and the now-unused `act`, `userEvent` imports and fake-timer `afterEach` if nothing else uses them). Add:

```tsx
describe('mailtoHref', () => {
  it('encodesSubjectAndBodyWithCrlfLineBreaks', () => {
    const href = mailtoHref({
      to: 'orders@sandwichshop.example',
      subject: 'Order Lunch, Fri 20 Nov',
      body: '1 × Ham & cheese\nAnna: Tuna?',
      final: true,
    })
    expect(href).toBe(
      'mailto:orders@sandwichshop.example' +
        '?subject=Order%20Lunch%2C%20Fri%2020%20Nov' +
        '&body=1%20%C3%97%20Ham%20%26%20cheese%0D%0AAnna%3A%20Tuna%3F',
    )
  })
})

// inside describe('EmailDraftView')
it('opensOutlookLinkWithDraft', async () => {
  render(<EmailDraftView onUnauthorized={vi.fn()} />)
  const link = await screen.findByRole('link', { name: 'Open in Outlook' })
  expect(link).toHaveAttribute('href', mailtoHref(draft))
  expect(screen.queryByRole('button', { name: /^Copy/ })).not.toBeInTheDocument()
})

it('disablesButtonWhenNoShopEmail', async () => {
  server.use(http.get('/api/admin/email-draft', () => HttpResponse.json({ ...draft, to: '  ' })))
  render(<EmailDraftView onUnauthorized={vi.fn()} />)
  expect(await screen.findByRole('button', { name: 'Open in Outlook' })).toBeDisabled()
  expect(screen.queryByRole('link', { name: 'Open in Outlook' })).not.toBeInTheDocument()
  expect(screen.getByText('No shop email set. Add it under Settings.')).toBeInTheDocument()
})
```

Keep `showsStillOpenNoteWhenNotFinal` and `hidesNoteWhenFinal` unchanged.

- [ ] **Step 2: Run tests to verify they fail**

Run: `npm --prefix frontend test -- --run EmailDraftView`
Expected: FAIL — `mailtoHref` is not exported / no "Open in Outlook" element.

- [ ] **Step 3: Implement in `frontend/src/EmailDraftView.tsx`**

- `mailtoHref(draft)`: `mailto:${draft.to}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body with \n replaced by \r\n)}`. `to` is not encoded.
- In the view: remove `Field`, `COPIED_MS`, `copied`/`copyError` state, the timer, `copy()`, `copyButton` and the copy-error alert. Keep To/Subject/Body visible as text.
- Next to the `<h2>Draft email</h2>`: if `draft.to.trim()` is non-empty, `<a className="btn btn-primary" href={mailtoHref(draft)}>Open in Outlook</a>`; otherwise `<button type="button" className="btn btn-primary" disabled>Open in Outlook</button>` plus `<p className="quiet">No shop email set. Add it under Settings.</p>`.

- [ ] **Step 4: Run the full frontend suite**

Run: `npm --prefix frontend test -- --run`
Expected: all tests pass, no TypeScript errors about unused imports.

- [ ] **Step 5: Check it in the running app**

With the app running (run-app skill), open http://localhost:5173 → Orders → Draft email. Hover "Open in Outlook" and confirm the `mailto:` URL; click it and confirm Outlook opens with To, Subject and multi-line Body filled in. Clear the shop email in Settings and confirm the button is greyed out with the note.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/EmailDraftView.tsx frontend/src/EmailDraftView.test.tsx
git commit -m "feat(frontend): open the order email in Outlook instead of copy buttons"
```
