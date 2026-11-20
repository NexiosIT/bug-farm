import { emailDraft, type EmailDraft } from './api'
import { useApiData } from './useApi'

interface Props {
  onUnauthorized: () => void
}

/** A mailto: link that opens the mail app with To, Subject and Body filled in. Line breaks are sent as CRLF (RFC 6068). */
export function mailtoHref(draft: EmailDraft): string {
  const subject = encodeURIComponent(draft.subject)
  const body = encodeURIComponent(draft.body.replace(/\r?\n/g, '\r\n'))
  return `mailto:${draft.to}?subject=${subject}&body=${body}`
}

export function EmailDraftView({ onUnauthorized }: Props) {
  const { data: draft, error } = useApiData(emailDraft, onUnauthorized)

  if (error) return <p role="alert">{error}</p>
  if (!draft) return <p className="quiet draft">Loading draft…</p>

  const hasShopEmail = draft.to.trim() !== ''

  return (
    <section className="draft" aria-label="Draft email">
      <p className="eyebrow">— For the shop</p>
      <h2>Draft email</h2>
      {hasShopEmail ? (
        <a className="btn btn-primary" href={mailtoHref(draft)}>
          Open in Outlook
        </a>
      ) : (
        <>
          <button type="button" className="btn btn-primary" disabled>
            Open in Outlook
          </button>
          <p className="quiet">No shop email set. Add it under Settings.</p>
        </>
      )}
      {!draft.final && <p className="callout">Ordering is still open — orders may still change.</p>}
      <dl className="info-grid">
        <div className="info-row">
          <dt>To</dt>
          <dd>
            <span>{draft.to}</span>
          </dd>
        </div>
        <div className="info-row">
          <dt>Subject</dt>
          <dd>
            <span>{draft.subject}</span>
          </dd>
        </div>
        <div className="info-row">
          <dt>Body</dt>
          <dd>
            <pre>{draft.body}</pre>
          </dd>
        </div>
      </dl>
    </section>
  )
}
