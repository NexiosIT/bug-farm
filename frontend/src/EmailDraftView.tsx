import { useEffect, useRef, useState } from 'react'
import { emailDraft } from './api'
import { useApiData } from './useApi'

interface Props {
  onUnauthorized: () => void
}

type Field = 'To' | 'Subject' | 'Body'

const COPIED_MS = 2000

export function EmailDraftView({ onUnauthorized }: Props) {
  const { data: draft, error } = useApiData(emailDraft, onUnauthorized)
  const [copied, setCopied] = useState<Field | null>(null)
  const [copyError, setCopyError] = useState<string | null>(null)
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)

  useEffect(() => () => clearTimeout(timer.current), [])

  async function copy(field: Field, text: string) {
    try {
      await navigator.clipboard.writeText(text)
      setCopyError(null)
      setCopied(field)
      clearTimeout(timer.current)
      timer.current = setTimeout(() => setCopied(null), COPIED_MS)
    } catch {
      setCopyError('Could not copy. Select the text and copy it by hand.')
    }
  }

  if (error) return <p role="alert">{error}</p>
  if (!draft) return <p>Loading draft…</p>

  const copyButton = (field: Field, text: string) => (
    <button type="button" aria-label={`Copy ${field}`} onClick={() => void copy(field, text)}>
      {copied === field ? 'Copied' : 'Copy'}
    </button>
  )

  return (
    <section className="draft" aria-label="Draft email">
      <h2>Draft email</h2>
      {!draft.final && <p className="note">Ordering is still open — orders may still change.</p>}
      <dl>
        <dt>To</dt>
        <dd>
          <span>{draft.to}</span> {copyButton('To', draft.to)}
        </dd>
        <dt>Subject</dt>
        <dd>
          <span>{draft.subject}</span> {copyButton('Subject', draft.subject)}
        </dd>
        <dt>Body</dt>
        <dd>
          <pre>{draft.body}</pre> {copyButton('Body', draft.body)}
        </dd>
      </dl>
      {copyError && <p role="alert">{copyError}</p>}
    </section>
  )
}
